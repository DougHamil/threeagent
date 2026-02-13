(ns threeagent.impl.frame-pacer)

(def ^:private known-rates
  "Common display refresh rates to snap calibration results to."
  #js [60 72 75 90 120 144 165 240 360])

(def ^:private calibration-target 20)
(def ^:private render-buffer-size 30)
(def ^:private drop-threshold 5)
(def ^:private promote-threshold 60)
(def ^:private promote-cooldown-ms 3000)
(def ^:private promote-headroom 0.85)
(def ^:private over-budget-ratio 0.90)
(def ^:private default-min-fps 10)

;; --- Internal helpers ---

(defn- median [^js arr]
  (let [sorted (.slice arr)]
    (.sort sorted)
    (let [len (.-length sorted)
          mid (js/Math.floor (/ len 2))]
      (if (odd? len)
        (aget sorted mid)
        (/ (+ (aget sorted (dec mid)) (aget sorted mid)) 2)))))

(defn- snap-to-known-rate [hz]
  (let [len (.-length known-rates)]
    (loop [i 0
           best-rate (aget known-rates 0)
           best-diff (js/Math.abs (- hz (aget known-rates 0)))]
      (if (>= i len)
        best-rate
        (let [rate (aget known-rates i)
              diff (js/Math.abs (- hz rate))]
          (if (< diff best-diff)
            (recur (inc i) rate diff)
            (recur (inc i) best-rate best-diff)))))))

(defn- detect-display-hz [^js samples]
  (let [med (median samples)
        raw-hz (/ 1000.0 med)]
    (snap-to-known-rate raw-hz)))

(defn- compute-tiers
  "Compute FPS tiers as integer divisors of display-hz, filtered by max/min fps."
  [display-hz max-fps min-fps]
  (let [effective-max (if max-fps (min max-fps display-hz) display-hz)
        result #js []]
    ;; Walk divisors from 1 upward
    (loop [divisor 1]
      (when (<= divisor display-hz)
        (let [fps (/ display-hz divisor)]
          (when (and (>= fps min-fps) (<= fps effective-max))
            (.push result fps))
          (recur (inc divisor)))))
    ;; Sort descending (highest fps first)
    (.sort result (fn [a b] (- b a)))
    result))

(defn- tier-budget-ms
  "Frame budget in ms for a given FPS tier."
  [fps]
  (/ 1000.0 fps))

(defn- drop-tier! [^js pacer now]
  (let [tiers (.-tiers pacer)
        new-idx (min (inc (.-tierIndex pacer)) (dec (.-length tiers)))]
    (set! (.-tierIndex pacer) new-idx)
    (set! (.-currentFps pacer) (aget tiers new-idx))
    (set! (.-frameInterval pacer) (tier-budget-ms (aget tiers new-idx)))
    (set! (.-overBudgetCount pacer) 0)
    (set! (.-underBudgetCount pacer) 0)
    (set! (.-lastTierChangeTime pacer) now)))

(defn- promote-tier! [^js pacer now]
  (let [tiers (.-tiers pacer)
        new-idx (max (dec (.-tierIndex pacer)) 0)]
    (set! (.-tierIndex pacer) new-idx)
    (set! (.-currentFps pacer) (aget tiers new-idx))
    (set! (.-frameInterval pacer) (tier-budget-ms (aget tiers new-idx)))
    (set! (.-overBudgetCount pacer) 0)
    (set! (.-underBudgetCount pacer) 0)
    (set! (.-lastTierChangeTime pacer) now)))

(defn- evaluate-tier-change! [^js pacer render-ms now]
  (let [tiers (.-tiers pacer)
        tier-idx (.-tierIndex pacer)
        current-budget (tier-budget-ms (.-currentFps pacer))
        actual-interval (.-actualInterval pacer)
        half-vblank (/ 500.0 (.-displayHz pacer))
        ;; Over budget if EITHER:
        ;; 1. CPU render time exceeds budget (CPU-bound)
        ;; 2. Actual frame interval missed the target vblank (GPU-bound)
        over-budget? (or (> render-ms (* over-budget-ratio current-budget))
                         (> actual-interval (+ current-budget half-vblank)))]
    ;; Check for drop: consecutive over-budget frames
    (if over-budget?
      (do
        (set! (.-overBudgetCount pacer) (inc (.-overBudgetCount pacer)))
        (set! (.-underBudgetCount pacer) 0)
        (when (and (>= (.-overBudgetCount pacer) drop-threshold)
                   (< tier-idx (dec (.-length tiers))))
          (drop-tier! pacer now)))
      ;; Not over budget — check for promote
      (do
        (set! (.-overBudgetCount pacer) 0)
        ;; Can we promote? Need a higher tier to exist
        (when (> tier-idx 0)
          (let [higher-fps (aget tiers (dec tier-idx))
                higher-budget (tier-budget-ms higher-fps)
                time-since-change (- now (.-lastTierChangeTime pacer))]
            (if (<= render-ms (* promote-headroom higher-budget))
              (do
                (set! (.-underBudgetCount pacer) (inc (.-underBudgetCount pacer)))
                (when (and (>= (.-underBudgetCount pacer) promote-threshold)
                           (>= time-since-change promote-cooldown-ms))
                  (promote-tier! pacer now)))
              (set! (.-underBudgetCount pacer) 0))))))))

(defn- finish-calibration! [^js pacer now]
  (let [samples (.-calibrationSamples pacer)
        display-hz (detect-display-hz samples)
        tiers (compute-tiers display-hz (.-maxFps pacer) (.-minFps pacer))]
    (set! (.-displayHz pacer) display-hz)
    (set! (.-tiers pacer) tiers)
    (set! (.-tierIndex pacer) 0)
    (set! (.-currentFps pacer) (aget tiers 0))
    (set! (.-frameInterval pacer) (tier-budget-ms (aget tiers 0)))
    (set! (.-lastFrameTime pacer) now)
    (set! (.-lastTierChangeTime pacer) now)
    (set! (.-overBudgetCount pacer) 0)
    (set! (.-underBudgetCount pacer) 0)
    (set! (.-phase pacer) "active")))

(defn- calibrate-tick! [^js pacer now]
  (let [last-time (.-calibrationLastTime pacer)]
    (when (> last-time 0)
      (let [interval (- now last-time)]
        ;; Discard outliers (tab backgrounding, initial junk)
        (when (and (> interval 2) (< interval 50))
          (.push (.-calibrationSamples pacer) interval)
          (set! (.-calibrationCount pacer) (inc (.-calibrationCount pacer))))))
    (set! (.-calibrationLastTime pacer) now)
    ;; Check if calibration is complete
    (when (>= (.-calibrationCount pacer) calibration-target)
      (finish-calibration! pacer now))))

;; --- Public API ---

(defn create
  "Create a new frame pacer. max-fps can be nil (no cap). min-fps defaults to 10."
  [max-fps min-fps]
  (let [pacer #js {:phase "calibrating"
                    :maxFps max-fps
                    :minFps (or min-fps default-min-fps)
                    :calibrationSamples #js []
                    :calibrationCount 0
                    :calibrationLastTime 0
                    :displayHz nil
                    :tiers nil
                    :tierIndex 0
                    :currentFps nil
                    :frameInterval nil
                    :lastFrameTime 0
                    :actualInterval 0
                    :renderTimes (js/Float64Array. render-buffer-size)
                    :renderTimeIndex 0
                    :renderTimeCount 0
                    :overBudgetCount 0
                    :underBudgetCount 0
                    :lastTierChangeTime 0}]
    pacer))

(defn should-render?
  "Returns true if this frame should be rendered. Call once per animation frame."
  [^js pacer now]
  (if (= "calibrating" (.-phase pacer))
    (do
      (calibrate-tick! pacer now)
      ;; Render every frame during calibration so scene appears immediately
      true)
    ;; Active phase — same logic as existing should-process-frame?
    (let [elapsed (- now (.-lastFrameTime pacer))
          frame-interval (.-frameInterval pacer)]
      (if (>= elapsed (- frame-interval 1.0))
        (do
          (set! (.-actualInterval pacer) elapsed)
          (set! (.-lastFrameTime pacer) now)
          true)
        false))))

(defn record-render-time!
  "Record a render time sample and evaluate tier changes."
  [^js pacer render-ms]
  (when (= "active" (.-phase pacer))
    (let [buf (.-renderTimes pacer)
          idx (.-renderTimeIndex pacer)]
      (aset buf (mod idx render-buffer-size) render-ms)
      (set! (.-renderTimeIndex pacer) (inc idx))
      (set! (.-renderTimeCount pacer) (min (inc (.-renderTimeCount pacer)) render-buffer-size))
      (evaluate-tier-change! pacer render-ms (js/performance.now)))))

(defn info
  "Return a snapshot of the pacer's current state."
  [^js pacer]
  (when pacer
    {:phase (.-phase pacer)
     :display-hz (.-displayHz pacer)
     :current-fps (.-currentFps pacer)
     :tier-index (.-tierIndex pacer)
     :tiers (when-let [t (.-tiers pacer)] (vec (array-seq t)))
     :over-budget-count (.-overBudgetCount pacer)
     :under-budget-count (.-underBudgetCount pacer)
     :actual-interval (.-actualInterval pacer)}))

(defn reset-pacer!
  "Reset the pacer, restarting calibration with new parameters."
  [^js pacer max-fps min-fps]
  (set! (.-phase pacer) "calibrating")
  (set! (.-maxFps pacer) max-fps)
  (set! (.-minFps pacer) (or min-fps default-min-fps))
  (set! (.-calibrationSamples pacer) #js [])
  (set! (.-calibrationCount pacer) 0)
  (set! (.-calibrationLastTime pacer) 0)
  (set! (.-displayHz pacer) nil)
  (set! (.-tiers pacer) nil)
  (set! (.-tierIndex pacer) 0)
  (set! (.-currentFps pacer) nil)
  (set! (.-frameInterval pacer) nil)
  (set! (.-lastFrameTime pacer) 0)
  (set! (.-actualInterval pacer) 0)
  (set! (.-renderTimeIndex pacer) 0)
  (set! (.-renderTimeCount pacer) 0)
  (set! (.-overBudgetCount pacer) 0)
  (set! (.-underBudgetCount pacer) 0)
  (set! (.-lastTierChangeTime pacer) 0)
  pacer)
