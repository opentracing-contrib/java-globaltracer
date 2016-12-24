# java-globaltracer
Global Tracer, forwarding to another Tracer implementation.

This library provides access to a `GlobalTracer`:

## GlobalTracer
This class provides the `GlobalTracer.get()` method that returns the singleton _global tracer_.  
Upon first use of any tracing method, this tracer lazily determines which `Tracer`
implementation to use:  
 1. If an explicitly configured tracer was provided via the `GlobalTracer.set()` method,
    that will always take precedence over automatically provided tracer instances.  
 2. A Tracer implementation can be automatically provided using the Java `ServiceLoader` through the
    `META-INF/services/io.opentracing.Tracer` service definition file.
    The GlobalTracer class will not attempt to choose between implementations;
    if more than one is found, a warning is logged and tracing is disabled by
    falling back to the default implementation:  
 3. If no tracer implementation is found, the `NoopTracer` will be used.

## How to use this library
Some examples on how this library can be used:

### Application intialization
Initialize a new tracer from the application configuration
and let it to become the `GlobalTracer` for the application:
````java
    // for example 'new BraveTracer()' from https://github.com/openzipkin/brave-opentracing
    Tracer configuredTracer = applicationConfiguration.buildConfiguredTracer();
    GlobalTracer.set(configuredTracer);
````

### Using the global tracer
Once initialized, all application code can instrument tracing by starting new spans like:
````java
    try (Span span = GlobalTracer.get().buildSpan("someOperation").start()) {
        // ... Traced block of code ...
    }
````

If no GlobalTracer is configured, this code will not throw any exceptions.
Tracing is simply delegated to the `NoopTracer` instead.

### Automatic Span propagation
This library _does not_ manage any span propagation.  
Consider combining this library with the [ActiveSpan library](https://github.com/opentracing-contrib/java-activespan)
if you want to implicitly manage and access the active span in your application.
