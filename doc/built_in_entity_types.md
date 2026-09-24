# Built-in Entity Types
Threeagent provides a number of entity-types out-of-the-box, allowing you to quickly get started building 3D scenes.

## Common

Every entity also takes `:visible` and `:frustum-culled`. `:frustum-culled false` stops Three.js skipping the object when its bounds are off screen, for objects a shader moves or draws beyond their geometry's bounds.

### `:object`

Properties: `:position` `:rotation` `:scale`

Corresponds to [Object3D](https://threejs.org/docs/index.html#api/en/core/Object3D)

### `:group`

Properties: `:position` `:rotation` `:scale`

Corresponds to [Group](https://threejs.org/docs/index.html#api/en/objects/Group)

### `:instance`

Properties: `:object`

Where `:object` is a valid THREE.js object instance (Mesh, Camera, Group, etc).

The value `:object` will be directly injected into the scene graph at this component's location.

## Geometry

Every geometry type also takes `:instances n`, which draws the mesh `n` times in one draw call. It's meant for [node material](shaders.md) shaders that place each instance themselves from `instance-index`, for example reading a storage buffer a compute shader fills:

```clojure
[:box {:instances particle-count
       :material {:shader particle}}]   ; particle reads (nth positions instance-index)
```

Its geometry's bounds say nothing about where those instances end up, so frustum culling is off for instanced meshes unless `:frustum-culled` says otherwise.

### `:box`

Properties: `:width` `:height` `:depth` `:width-segments` `:height-segments` `:depth-segments` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to BoxGeometry

### `:plane`

Properties: `:width` `:height` `:width-segments` `:height-segments` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to PlaneGeometry

### `:sphere`

Properties: `:radius` `:phi-start` `:phi-length` `:theta-start` `:theta-length` `:width-segments` `:height-segments` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to SphereGeometry

### `:cylinder`

Properties: `:radius-top` `:radius-bottom` `:height` `:radial-segments` `:height-segments` `:open-ended?` `:theta-start` `:theta-length` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to CylinderGeometry

### `:circle`

Properties: `:radius` `:segments` `:theta-start` `:theta-length` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to CircleGeometry

### `:cone`

Properties: `:radius` `:height` `:radial-segments` `:height-segments` `:open-ended?` `:theta-start` `:theta-length` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to ConeGeometry

### `:dodecahedron`

Properties: `:radius` `:detail` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to DodecahedronGeometry

### `:icosahedron`

Properties: `:radius` `:detail` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to IcosahedronGeometry

### `:octahedron`

Properties: `:radius` `:detail` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to OctahedronGeometry

### `:tetrahedron`

Properties: `:radius` `:detail` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to TetrahedronGeometry

### `:ring`

Properties: `:inner-radius` `:outer-radius` `:theta-segments` `:phi-segments` `:theta-start` `:theta-length` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to RingGeometry

### `:torus`

Properties: `:radius` `:tube` `:radial-segments` `:tubular-segments` `:arc` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to TorusGeometry

### `:torus-knot`

Properties: `:radius` `:tube` `:radial-segments` `:tubular-segments` `:p` `:q` `:material` `:cast-shadow` `:receive-shadow`

Corresponds to TorusKnotGeometry

### `:shape`

Properties: `:shape` `:material` `:cast-shadow` `:receive-shadow`

Where `:shape` is a valid THREE.js Shape.

Corresponds to ShapeGeometry

## Lights

### `:ambient-light`

Properties: `:color` `:intensity`

Corresponds to AmbientLight

### `:point-light`

Properties: `:color` `:intensity` `:distance` `:decay` `:cast-shadow` `:shadow`

Corresponds to PointLight

### `:directional-light`

Properties: `:color` `:intensity` `:cast-shadow` `:shadow`

Corresponds to DirectionalLight

### `:hemisphere-light`

Properties: `:sky-color` `:ground-color` `:intensity`

Corresponds to HemisphereLight

### `:rect-area-light`

Properties: `:color` `:intensity` `:width` `:height`

Corresponds to RectAreaLight

### `:spot-light`

Properties: `:color` `:intensity` `:distance` `:angle` `:penumbra` `:decay` `:cast-shadow` `shadow`

Corresponds to SpotLight

## Cameras

### `:perspective-camera`

Properties: `:fov` `:aspect` `:near` `:far` `:material`

Corresponds to PerspectiveCamera

### `:orthographic-camera`

Properties: `:left` `:right` `:top` `:bottom` `:near` `:far` `:material`

Corresponds to OrthographicCamera


