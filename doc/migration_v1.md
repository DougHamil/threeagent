# Migrating to v1.x

When migrating a project from Threeagent v0.x to v1.x, there are a few changes to the API that
we'll need to consider.

## Accessing the ThreeJS Renderer, Scene, and Canvas

In v0.x, the `threeagent.core/render` function returned a JavaScript object that captured the
Threeagent context. This object is no longer returned, as it leaked implementation details that
we should not depend on.

In v1.x, the `threeagent.core/render` function returns a Clojure map with these keys:
* `:threejs-renderer` the ThreeJS Renderer instance that Threeagent created
* `:threejs-scene` the ThreeJS Scene instance that Threeagent created
* `:threejs-default-camera` The default ThreeJS Camera instance that Threeagent created. This is only useful
if we don't add our own camera to the scene with the `:perspective-camera` or `:orthographic-camera` entity
types.
* `:canvas` the canvas DOM element used by Threeagent. This will be a new canvas, if a canvas DOM element was
not provided to the function call.

## Use IEntityType protocol instead of defcomponent macro

In v0.x the `threeagent.macros/defcomponent` macro was used to register new entity types. In v1.x, this has
been replaced by the `threeagent.entity/IEntity` protocol, which should be implemented and passed into the
`threeagent.core/render` options parameter.

Please see [this page](./extending_threeagent.md) for more information.
