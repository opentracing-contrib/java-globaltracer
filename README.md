[![Build Status][ci-img]][ci] [![Released Version][maven-img]][maven]

# _:exclamation: deprecated :exclamation:_ Global tracer resolution for Java

Global Tracer forwarding to another Tracer implementation.

**:exclamation:Note:** `GlobalTracer` has been included in core 
OpenTracing Java since [opentracing-java] version `0.21.0`.  
**_This contrib project is now deprecated._**

## GlobalTracer
Provides the `GlobalTracer.get()` method that returns the singleton _global tracer_.  

When the tracer is needed it is lazily looked up using the following rules:
 1. The tracer from the last `register(tracer)` call always takes precedence.</li>
 2. If no tracer was registered, one resolved by the [TracerResolver].
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

  [ci-img]: https://img.shields.io/travis/opentracing-contrib/java-globaltracer/master.svg
  [ci]: https://travis-ci.org/opentracing-contrib/java-globaltracer
  [maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-globaltracer.svg
  [maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-globaltracer
  [opentracing-java]: https://github.com/opentracing/opentracing-java
  [tracerresolver]: https://github.com/opentracing-contrib/java-tracerresolver
