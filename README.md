# java-globaltracer
Global Tracer, forwarding to another Tracer implementation.

This library provides access to a `GlobalTracer`:

## GlobalTracer
This class has the following purpose:
 1. The `GlobalTracer.tracer()` factory method returning the singleton _global tracer_.
    If there is no global tracer, a `NoopTracer` is returned instead.
 2. Enrich the lifecycle of created `Span` objects through the global Tracer 
    to update the _active span_ as they are started and finished.
 3. Utility `traced()` methods to create Runnable and Callable instances 
    that run within a new Span that is _child of the active span_ form the scheduling thread.
    An `operationName` must be provided for a new Span to be created.

## How to use this library

  _TODO: rewrite the examples!_

