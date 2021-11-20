# Accessing ThreeJS Objects

Often times, we'll want to access the actual ThreeJS objects that Threeagent is managing, so that
we can leverage ThreeJS functionality. For instance, we might want to use ThreeJS's raycaster to
pick objects in our scene, or use a physics simulation to directly drive the transformation of each object.

Threeagent provides a few ways to get access to the ThreeJS substrate.

## Lifecycle Hooks

We can set the `:on-added` and `:on-removed` of any entity to a callback function that will be invoked with
the ThreeJS object for that entity.  The `:on-added` callback will be invoked after the object is first 
added to the scene graph. The `:on-removed` callback will be invoked after the object is removed from the
scene graph.

There is also `:ref` which is an alias for `:on-added`.

## Extending Threeagent

For more advanced use cases, we can define custom `ISystem` and `IEntityType` implementations to create
and manage ThreeJS objects. See [Extending Threeagent](./extending_threeagent.md) for more information.
