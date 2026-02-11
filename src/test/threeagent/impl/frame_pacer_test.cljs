(ns threeagent.impl.frame-pacer-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [threeagent.impl.frame-pacer :as fp]))

;; --- Helpers ---

(defn- simulate-calibration
  "Feed calibration samples to the pacer at the given interval-ms.
   Returns the pacer after calibration completes."
  [pacer interval-ms]
  (let [start 1000.0]
    (dotimes [i 21]
      (fp/should-render? pacer (+ start (* i interval-ms))))
    pacer))

;; --- Calibration tests ---

(deftest calibration-renders-every-frame
  (testing "During calibration, should-render? always returns true"
    (let [pacer (fp/create nil nil)]
      (is (= "calibrating" (.-phase pacer)))
      (is (true? (fp/should-render? pacer 1000.0)))
      (is (true? (fp/should-render? pacer 1001.0)))
      (is (true? (fp/should-render? pacer 1002.0))))))

(deftest calibration-detects-60hz
  (testing "20 samples at ~16.67ms intervals detects 60Hz"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667)
      (is (= "active" (.-phase pacer)))
      (is (= 60 (.-displayHz pacer))))))

(deftest calibration-detects-120hz
  (testing "20 samples at ~8.33ms intervals detects 120Hz"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 8.333)
      (is (= "active" (.-phase pacer)))
      (is (= 120 (.-displayHz pacer))))))

(deftest calibration-detects-144hz
  (testing "20 samples at ~6.94ms intervals detects 144Hz"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 6.944)
      (is (= "active" (.-phase pacer)))
      (is (= 144 (.-displayHz pacer))))))

(deftest calibration-discards-outliers
  (testing "Outlier intervals (<2ms or >50ms) are discarded"
    (let [pacer (fp/create nil nil)
          start 1000.0]
      ;; Feed some outliers mixed with good samples
      (fp/should-render? pacer start)
      ;; Outlier: 1ms gap
      (fp/should-render? pacer (+ start 1.0))
      ;; Outlier: 100ms gap (tab backgrounding)
      (fp/should-render? pacer (+ start 101.0))
      ;; Now feed 21 good samples
      (dotimes [i 21]
        (fp/should-render? pacer (+ start 200.0 (* i 16.667))))
      (is (= "active" (.-phase pacer)))
      (is (= 60 (.-displayHz pacer))))))

;; --- Tier computation tests ---

(deftest tiers-60hz-no-cap
  (testing "60Hz with no cap produces correct tiers"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667)
      (let [tiers (.-tiers pacer)]
        ;; Should include 60, 30, 20, 15, 12, 10
        (is (= 60 (aget tiers 0)))
        (is (= 30 (aget tiers 1)))
        (is (= 20 (aget tiers 2)))
        (is (= 15 (aget tiers 3)))
        (is (= 12 (aget tiers 4)))
        (is (= 10 (aget tiers 5)))))))

(deftest tiers-60hz-with-30fps-cap
  (testing "60Hz with 30fps cap starts at 30fps tier"
    (let [pacer (fp/create 30 nil)]
      (simulate-calibration pacer 16.667)
      (let [tiers (.-tiers pacer)]
        (is (= 30 (aget tiers 0)))
        (is (= 30 (.-currentFps pacer)))))))

(deftest tiers-120hz-no-cap
  (testing "120Hz produces tiers including 120, 60, 40, 30, etc."
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 8.333)
      (let [tiers (.-tiers pacer)]
        (is (= 120 (aget tiers 0)))
        (is (= 60 (aget tiers 1)))
        (is (= 40 (aget tiers 2)))
        (is (= 30 (aget tiers 3)))))))

;; --- Frame decision tests ---

(deftest active-phase-frame-skipping
  (testing "After calibration, frames are skipped based on tier interval"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667)
      ;; After calibration, tier is 60fps = 16.667ms interval
      (let [base (.-lastFrameTime pacer)]
        ;; Too soon - should skip
        (is (false? (fp/should-render? pacer (+ base 5.0))))
        ;; Just right (within 1ms tolerance)
        (is (true? (fp/should-render? pacer (+ base 16.0))))))))

;; --- Drop tier tests ---

(deftest drop-tier-after-consecutive-overbudget
  (testing "Drops to lower tier after 5 consecutive over-budget frames"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667)
      (is (= 60 (.-currentFps pacer)))
      ;; Simulate 5 consecutive frames that take >90% of 16.667ms budget
      (dotimes [_ 5]
        (fp/record-render-time! pacer 16.0))
      (is (= 30 (.-currentFps pacer))))))

(deftest no-drop-on-single-spike
  (testing "A single over-budget frame doesn't trigger a drop"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667)
      (is (= 60 (.-currentFps pacer)))
      ;; One heavy frame, then normal frames
      (fp/record-render-time! pacer 16.0)
      (fp/record-render-time! pacer 5.0)
      (is (= 60 (.-currentFps pacer))))))

(deftest no-drop-on-intermittent-spikes
  (testing "Intermittent spikes (not consecutive) don't trigger a drop"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667)
      (is (= 60 (.-currentFps pacer)))
      ;; 4 heavy, 1 light, 4 heavy — never 5 consecutive
      (dotimes [_ 4]
        (fp/record-render-time! pacer 16.0))
      (fp/record-render-time! pacer 5.0)
      (dotimes [_ 4]
        (fp/record-render-time! pacer 16.0))
      (is (= 60 (.-currentFps pacer))))))

;; --- GPU-bound drop tests ---

(deftest drop-tier-on-gpu-bottleneck
  (testing "Drops tier when actual frame intervals miss vblank despite fast CPU render"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 8.333) ;; 120Hz
      (is (= 120 (.-currentFps pacer)))
      ;; GPU bottleneck: CPU render is fast (2ms) but actual intervals are ~25ms
      ;; (missing target 8.33ms vblank completely)
      ;; half-vblank at 120Hz = 4.17ms, threshold = 8.33 + 4.17 = 12.5ms
      (let [base (.-lastFrameTime pacer)]
        (dotimes [i 5]
          (let [now (+ base (* (inc i) 25.0))]
            (fp/should-render? pacer now)
            (fp/record-render-time! pacer 2.0))))
      ;; Should have dropped from 120fps
      (is (< (.-currentFps pacer) 120)))))

(deftest gpu-bottleneck-drops-to-sustainable-tier
  (testing "On a 120Hz display with ~25ms actual intervals, drops until 40fps tier"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 8.333) ;; 120Hz
      (is (= 120 (.-currentFps pacer)))
      ;; Simulate sustained GPU bottleneck at ~25ms per frame
      ;; 120fps → drop → 60fps → drop → 40fps (25ms budget, on target)
      (let [base (.-lastFrameTime pacer)]
        (dotimes [i 20]
          (let [now (+ base (* (inc i) 25.0))]
            (when (fp/should-render? pacer now)
              (fp/record-render-time! pacer 2.0)))))
      ;; Should settle at 40fps (25ms = 120Hz/3) since actual intervals are ~25ms
      ;; which equals the 40fps budget (not over-budget at that tier)
      (is (<= (.-currentFps pacer) 40)))))

(deftest no-gpu-drop-when-on-target
  (testing "No drop when actual intervals match the current tier's vblank interval"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 8.333) ;; 120Hz
      ;; Force down to 40fps tier first
      (dotimes [_ 5]
        (fp/record-render-time! pacer 16.0)) ;; CPU over-budget at 120fps
      (dotimes [_ 5]
        (fp/record-render-time! pacer 16.0)) ;; CPU over-budget at 60fps
      (is (= 40 (.-currentFps pacer)))
      ;; Now simulate perfect 40fps: actual intervals = 25ms, CPU render = 2ms
      ;; 25ms budget, half-vblank = 4.17ms, threshold = 29.17ms
      ;; 25ms < 29.17ms → NOT over budget
      (let [base (.-lastFrameTime pacer)]
        (dotimes [i 10]
          (let [now (+ base (* (inc i) 25.0))]
            (fp/should-render? pacer now)
            (fp/record-render-time! pacer 2.0))))
      (is (= 40 (.-currentFps pacer))))))

(deftest actual-interval-stored-by-should-render
  (testing "should-render? stores actual elapsed interval"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667) ;; 60Hz
      (let [base (.-lastFrameTime pacer)]
        ;; Render at 20ms interval (slightly late)
        (fp/should-render? pacer (+ base 20.0))
        (is (< (js/Math.abs (- (.-actualInterval pacer) 20.0)) 0.01))))))

;; --- Promote tier tests ---

(deftest promote-tier-after-sustained-headroom
  (testing "Promotes to higher tier after sustained headroom + cooldown"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667)
      ;; Force drop to 30fps
      (dotimes [_ 5]
        (fp/record-render-time! pacer 16.0))
      (is (= 30 (.-currentFps pacer)))
      ;; Manually set lastTierChangeTime far in the past to bypass cooldown
      (set! (.-lastTierChangeTime pacer) (- (js/performance.now) 5000))
      ;; Simulate 60 consecutive frames well within 60fps budget (85% of 16.667ms = 14.17ms)
      (dotimes [_ 60]
        (fp/record-render-time! pacer 10.0))
      (is (= 60 (.-currentFps pacer))))))

(deftest no-promote-during-cooldown
  (testing "Does not promote during cooldown period"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667)
      ;; Force drop to 30fps
      (dotimes [_ 5]
        (fp/record-render-time! pacer 16.0))
      (is (= 30 (.-currentFps pacer)))
      ;; Do NOT bypass cooldown — lastTierChangeTime is recent
      ;; 60 light frames, but within cooldown
      (dotimes [_ 60]
        (fp/record-render-time! pacer 5.0))
      ;; Should still be at 30fps because cooldown hasn't elapsed
      (is (= 30 (.-currentFps pacer))))))

;; --- Reset tests ---

(deftest reset-restarts-calibration
  (testing "reset! puts pacer back into calibrating phase"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667)
      (is (= "active" (.-phase pacer)))
      (fp/reset-pacer! pacer nil nil)
      (is (= "calibrating" (.-phase pacer)))
      (is (nil? (.-displayHz pacer)))
      (is (nil? (.-tiers pacer))))))

(deftest reset-applies-new-max-fps
  (testing "reset! applies new max-fps parameter"
    (let [pacer (fp/create nil nil)]
      (simulate-calibration pacer 16.667)
      (fp/reset-pacer! pacer 30 nil)
      (simulate-calibration pacer 16.667)
      (is (= 30 (.-currentFps pacer))))))

;; --- Min FPS floor tests ---

(deftest min-fps-floor-respected
  (testing "Tiers don't go below min-fps"
    (let [pacer (fp/create nil 15)]
      (simulate-calibration pacer 16.667)
      (let [tiers (.-tiers pacer)
            lowest (aget tiers (dec (.-length tiers)))]
        (is (>= lowest 15))))))
