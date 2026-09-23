(ns threeagent.tsl.material
  "Node materials described as data.

     {:shader   my-shader         ; (fn [uniforms] node-or-output-map)
      :type     :standard         ; see `material-types`, default :basic
      :uniforms {:time 0 :tint [1 0 0]}
      :transparent true           ; any other key is a material property
      :blending :additive}

   The shader fn receives a map of uniform nodes keyed like `:uniforms` and
   returns a node (the material's colorNode) or a map of outputs:
   `{:color .. :opacity .. :position .. :normal .. :emissive .. :fragment ..}`,
   each key `k` setting the material's `<k>Node` property.

   Materials without `:uniforms` are shared between every owner using the same
   spec. Materials with `:uniforms` are per owner. Changing only uniform values
   writes `uniform.value` and never rebuilds the shader. Changing the shader
   fn, the type, a property, or the set/kind of uniforms builds a new material.
   So a hot reload that redefines the shader fn swaps materials in place the
   next time the scene renders."
  (:require ["three/webgpu" :as three]
            ["three/tsl" :as tsl-module]
            [clojure.string :as str]
            [threeagent.tsl :as tsl]))

(def material-types
  {:basic three/MeshBasicNodeMaterial
   :standard three/MeshStandardNodeMaterial
   :physical three/MeshPhysicalNodeMaterial
   :phong three/MeshPhongNodeMaterial
   :lambert three/MeshLambertNodeMaterial
   :toon three/MeshToonNodeMaterial
   :matcap three/MeshMatcapNodeMaterial
   :normal three/MeshNormalNodeMaterial
   :sss three/MeshSSSNodeMaterial
   :sprite three/SpriteNodeMaterial
   :points three/PointsNodeMaterial
   :line three/LineBasicNodeMaterial
   :line-dashed three/LineDashedNodeMaterial
   :line2 three/Line2NodeMaterial
   :volume three/VolumeNodeMaterial
   :shadow three/ShadowNodeMaterial
   :node three/NodeMaterial})

(def ^:private enum-values
  {:blending {:none three/NoBlending
              :normal three/NormalBlending
              :additive three/AdditiveBlending
              :subtractive three/SubtractiveBlending
              :multiply three/MultiplyBlending}
   :side {:front three/FrontSide
          :back three/BackSide
          :double three/DoubleSide}})

(defn spec?
  "True when `x` describes a node material."
  [x]
  (and (map? x) (contains? x :shader)))

;; ---------------------------------------------------------------------------
;; Conversions
;; ---------------------------------------------------------------------------

(defn- camel [k]
  (let [[head & more] (str/split (name k) #"-")]
    (apply str head (map str/capitalize more))))

(defn- material-ctor [t]
  (cond
    (keyword? t) (or (get material-types t)
                     (throw (js/Error. (str "Unknown node material :type " t
                                            ", expected one of " (keys material-types)))))
    (fn? t) t
    :else (throw (js/Error. (str "Invalid node material :type " t)))))

(defn- props->js [props]
  (reduce-kv (fn [^js o k v]
               (let [v (if (keyword? v)
                         (or (get-in enum-values [k v])
                             (throw (js/Error. (str "Unknown value " v " for material property " k))))
                         v)]
                 (unchecked-set o (camel k) v)
                 o))
             #js {}
             props))

(defn- apply-outputs! [^js mat out]
  (if (map? out)
    (doseq [[k v] out]
      (unchecked-set mat (str (camel k) "Node") v))
    (set! (.-colorNode mat) out)))

;; ---------------------------------------------------------------------------
;; Uniforms
;; ---------------------------------------------------------------------------

(defn- uniform-kind [v]
  (cond
    (number? v) :number
    (boolean? v) :boolean
    (and (vector? v) (<= 2 (count v) 4)) (keyword (str "vec" (count v)))
    (tsl/node? v) [:node v]
    (and (some? v) (.-isTexture ^js v)) :texture
    (some? v) [:object (.-constructor ^js v)]
    :else (throw (js/Error. "Uniform values cannot be nil"))))

(defn- vec->three [[x y z w :as v]]
  (case (count v)
    2 (three/Vector2. x y)
    3 (three/Vector3. x y z)
    4 (three/Vector4. x y z w)))

(defn- create-uniform [v]
  (case (uniform-kind v)
    (:number :boolean) (tsl-module/uniform v)
    (:vec2 :vec3 :vec4) (tsl-module/uniform (vec->three v))
    :texture (tsl-module/texture v)
    (if (tsl/node? v) v (tsl-module/uniform v))))

(defn- set-uniform! [^js node v]
  (case (uniform-kind v)
    (:vec2 :vec3 :vec4) (let [^js target (.-value node)
                              [x y z w] v]
                          (case (count v)
                            2 (.set target x y)
                            3 (.set target x y z)
                            4 (.set target x y z w)))
    (if (tsl/node? v)
      nil
      (set! (.-value node) v))))

(defn- uniform-signature [uniforms]
  (into {} (map (fn [[k v]] [k (uniform-kind v)])) uniforms))

;; ---------------------------------------------------------------------------
;; Cache and ownership
;; ---------------------------------------------------------------------------

(defonce ^:private shared (atom {}))          ; key -> #js {:material :refs}
(defonce ^:private owners (js/WeakMap.))       ; owner -> #js {:key :material :uniforms :shared}

(defn- structural-key [spec]
  [(:shader spec)
   (:type spec :basic)
   (dissoc spec :shader :type :uniforms)
   (uniform-signature (:uniforms spec))])

(defn- build [spec uniform-nodes]
  (let [ctor (material-ctor (:type spec :basic))
        ^js mat (new ctor)
        out ((:shader spec) uniform-nodes)]
    (.setValues mat (props->js (dissoc spec :shader :type :uniforms)))
    (apply-outputs! mat out)
    mat))

(defn- acquire-shared [key spec]
  (if-let [^js entry (get @shared key)]
    (do (set! (.-refs entry) (inc (.-refs entry)))
        (.-material entry))
    (let [mat (build spec {})]
      (swap! shared assoc key #js {:material mat :refs 1})
      mat)))

(defn- release-shared [key]
  (when-let [^js entry (get @shared key)]
    (set! (.-refs entry) (max 0 (dec (.-refs entry))))))

(defn release!
  "Drop `owner`'s claim on its material. Per-owner materials are disposed;
   shared ones stay cached until `dispose-unused!`."
  [owner]
  (when-let [^js state (.get owners owner)]
    (.delete owners owner)
    (if (.-shared state)
      (release-shared (.-key state))
      (.dispose ^js (.-material state)))))

(defn acquire!
  "Material for `owner` (any object, typically the Mesh) described by `spec`."
  [owner spec]
  (release! owner)
  (let [key (structural-key spec)
        uniforms (:uniforms spec)]
    (if (empty? uniforms)
      (let [mat (acquire-shared key spec)]
        (.set owners owner #js {:key key :material mat :shared true})
        mat)
      (let [nodes (reduce-kv (fn [m k v] (assoc m k (create-uniform v))) {} uniforms)
            mat (build spec nodes)]
        (.set owners owner #js {:key key :material mat :uniforms nodes :shared false})
        mat))))

(defn update!
  "Bring `owner`'s material in line with `spec`: writes uniform values when only
   they changed, otherwise builds (or finds) a new material."
  [owner spec]
  (let [^js state (.get owners owner)]
    (if (and state (= (.-key state) (structural-key spec)))
      (do
        (doseq [[k v] (:uniforms spec)]
          (set-uniform! (get (.-uniforms state) k) v))
        (.-material state))
      (acquire! owner spec))))

(defn material-of
  "The material currently held by `owner`, if any."
  [owner]
  (when-let [^js state (.get owners owner)]
    (.-material state)))

(defn uniforms-of
  "The uniform nodes of `owner`'s material, keyed like its spec's `:uniforms`.
   Handy for driving values imperatively (e.g. every frame from a system)."
  [owner]
  (when-let [^js state (.get owners owner)]
    (.-uniforms state)))

(defn dispose-unused!
  "Dispose shared materials no owner holds anymore. threeagent calls this when
   a scene is reset (hot reload)."
  []
  (swap! shared (fn [m]
                  (reduce-kv (fn [m k ^js entry]
                               (if (zero? (.-refs entry))
                                 (do (.dispose ^js (.-material entry))
                                     (dissoc m k))
                                 m))
                             m m))))
