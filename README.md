# java-globaltracer
Global Tracer, forwarding to another Tracer implementation.

This library provides access to a `GlobalTracer`:

## GlobalTracer
This class has the following purpose:
 1. The `GlobalTracer.tracer()` method returning the singleton _global tracer_.  
    Upon first use of any tracing method, this tracer lazily determines which `Tracer`
    implementation to use:  
    a) If an explicitly configured tracer was provided via the `GlobalTracer.setTracer()` method,
    that will always take precedence over automatically provided tracer instances.  
    b) A Tracer implementation can be automatically provided using the Java `ServiceLoader` through the
    `META-INF/services/io.opentracing.Tracer` service definition file.
    The GlobalTracer class will not attempt to choose between implementations;
    if more than one is found, a warning is logged and tracing is disabled by
    falling back to the default implementation:  
    c) If no tracer implementation is found, the `NoopTracer` will be used.
 2. Utility `traced()` methods to create `Runnable` and `Callable` instances that run within 
    a new Span obtained from the global tracer.

## How to use this library
Some examples on how this library can be used:

### Application intialization
Initialize a new tracer from the application configuration
and let it to become the `GlobalTracer` for the application:
````java
    // for example 'new BraveTracer()' from https://github.com/openzipkin/brave-opentracing
    Tracer configuredTracer = applicationConfiguration.buildConfiguredTracer();
    GlobalTracer.setTracer(configuredTracer);
````

### Using the global tracer
Once initialized, all application code can instrument tracing by starting new spans like:
````java
    try (Span span = GlobalTracer.tracer().buildSpan("someOperation").start()) {
        // Code executing here is part of the 'someOperation' Span.
        // This span will be closed, regardless of any exceptions thrown here.
    }
````

If no GlobalTracer is configured, this code will not throw any exceptions.
Tracing is simply delegated to the `NoopTracer` instead.

### Automatic Span propagation
This library only provides access to a global tracer and _does not_ manage any span propagation.  
Consider combining this library with the [ActiveSpan library](https://github.com/opentracing-contrib/java-activespan)
if you want to implicitly manage and access the active span in your application.
