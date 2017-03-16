[![Build Status][ci-img]][ci] [![Released Version][maven-img]][maven]

# Global tracer resolution for Java
Global Tracer forwarding to another Tracer implementation.

## GlobalTracer
Provides the `GlobalTracer.get()` method that returns the singleton _global tracer_.  

When the tracer is needed it is lazily looked up using the following rules:
 1. The tracer from the last `register(tracer)` or `update(tracer -> tracer)` call always takes precedence.</li>
 2. If no tracer was registered, one is looked up from the `ServiceLoader`.  
    The GlobalTracer will not attempt to choose between implementations:
 3. If no single implementation is found, the `NoopTracer` will be used.

## How to use this library
Some examples on how this library can be used:

### Application intialization
Initialize a new tracer from the application configuration
and let it to become the `GlobalTracer` for the application:
```java
    Tracer configuredTracer = applicationConfiguration.buildConfiguredTracer();
    GlobalTracer.register(configuredTracer);
```

### Using the global tracer
Once initialized, all application code can instrument tracing by starting new spans like:
```java
    try (Span span = GlobalTracer.get().buildSpan("someOperation").start()) {
        // ... Traced block of code ...
    }
```

If no GlobalTracer is configured, this code will not throw any exceptions.
Tracing is simply delegated to the `NoopTracer` instead.

### Updating the registered Tracer
To register a new tracer, based on the current registered tracer, use the `GlobalTracer.update` method:
```java
    GlobalTracer.update(tracer -> new WrappingTracer(tracer));
```

This creates a wrapper around the actual underlying `Tracer` implementation.  
A wrapper around `GlobalTracer` itself should not be registered as that will result in an infinite recursive cycle
when accessed.

### Automatic Span propagation
This library _does not_ manage any span propagation.  
Consider combining this library with the [SpanManager library](https://github.com/opentracing-contrib/java-spanmanager)
if you want to implicitly manage and access the active span in your application.

  [ci-img]: https://img.shields.io/travis/opentracing-contrib/java-globaltracer/master.svg
  [ci]: https://travis-ci.org/opentracing-contrib/java-globaltracer
  [maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-globaltracer.svg
  [maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-globaltracer
