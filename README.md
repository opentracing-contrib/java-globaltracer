# java-globaltracer
Global ThreadLocal Span for implicit propagation, delegating to another Tracer implementation.

This library provides two main utiltiy classes; `GlobalTracer` and `ActiveSpanManager`.
The first is intended for used by application code and library developers,
where the latter is more intended for boundary filters and `Tracer` implementations.

## GlobalTracer
This utility class has three main purposes:
 1. Standardized static access to the configured global tracer in the application:
    `GlobalTracer.tracer()`.
    This method never returns a `null` reference, but at the very least a `NoopTracer` instance.
 2. Providing a Java SPI ServiceLoader support for compatible `Tracer` implementations
    that contain a `META-INF/services/io.opentracing.Tracer` entry.
    In case there are no (or multiple) Tracer implementations, 
    a configured tracer can be registered explicitly by calling `GlobalTracer.register()`.
 3. Wrapping the configured global tracer implementation so all `Span` instances
    created from it are automatically registered as _active span_ and deregistered
    when they finish.

## ActiveSpanManager
This utility class provides the following functionality.
 1. The `ActiveSpanManager.activate()` static method that is automatically called from
    the `GlobalTracer` instance. 
 2. The `Closable` result from this `activate()` method will deactviate the given `Span`
    if needed. However, the `Spans` returned by the `GlobalTracer` will also make sure
    they get deactivated whenever they're finished or closed.
 3. A method `ActiveSpanManager.activeSpan()` that will return the _active span_ 
    from anywhere in the code. This may be useful for libraries that wish to add tags
    or log information in the active span.
 4. It contains a default implementation that uses `ThreadLocal` storage to manage
    the globally _active span_, activating and deactivating it at the appropriate
    moments (starting / closing spans etc).
 5. `Tracer` implementations or applications that require more customized span management
    can provide their own subclass of `ActiveSpanManager` and either register it 
    programmatically through the `ActiveSpanManager.registerInstance()` method 
    or automatically by the Java SPI ServiceLoader via a 
    `META-INF/services/io.opentracing.global.ActiveSpanManager` service definition.

## How to use this library
This section tries to give some basic examples on how to use the Global tracer and
identify some caveats that need to be taken into account.

### From miscelaneous application / library code
To add tracing functionality to code that cannot manually propagate `Tracer` or `Span` instances
(e.g. due to API restrictions or absence of dependency injection techniques)
simply refer to the global tracer instead; any created `Span` will be made `child-of` the currently _active span_.
````java
try (Span span = GlobalTracer.tracer().buildSpan("someOperation").start()) {
    
    // ..traced code block here..
    // calls to ActiveSpanManager.activeSpan() here get the new span reference
    // the new span is automatically a 'child-of' any previously active span (if applicable).

}
// calls to ActiveSpanManager.activeSpan() here will receive the active span from before the try block.
````
Due to the `try-with-resources` construct, the span will automatically close and restore the _active span_ state to what it was previously.  
It should be noted that `Tracer` implementations need not be concerned with this functionality;
it is provided by the `ActiveSpanManager` being invoked from the global tracer.

### From inbound filters at the application boundary (server)
To create a generic _opentracing_ compatible filter, the hypothetical filter could look similar to
the following fragment:
````java
public void doFilter(someContext, someFilterChain) {
    // Obtain the inbound context:
    Format<Carrier> someFormat = ... // usually known by filter
    Carrier someCarrier = ... // Usually obtained from someContext
    SpanContext inboundContext = GlobalTracer.tracer().extract(someFormat, someCarrier);
    
    // Create a new span as child of the inbound context.
    try (Span span = GlobalTracer.tracer().buildSpan("inboundOperation")
            .asChildOf(inboundContext).start()) {
        someFilterChain.filter(someContext); // continue the filtered operation within 'span'
    } finally { // Clears any active spans.
        // This is mostly a safety measure that can be taken in outermost filters
        // due to the use of threadpools on most application platforms.
        // clearActiveSpans() can be called as cleanup before returning a thread back to the pool.
        ActiveSpanManager.clearActiveSpans();
    }
}
````

### From outbound filters at the application boundary (client)
The global tracer can also be used to inject the _active span_'s context into outbound requests:
````java
// Depending on the type of client, gather the format and carrier to propagate SpanContext with.
Format<Carrier> someFormat = ...
Carrier someCarrier = ...
// Leave the injection to the actual tracer implementation:
GlobalTracer.tracer().inject(ActiveSpanManager.activeSpan(), someFormat, someCarrier);
// Call the actual client with the injected SpanContext in the carrier.
````

## Default in-process propagation
By default, starting a new java `Thread` will lose the _active span_ from this library.
Therefore there are several utility classes provided in the `global.concurrent` subpackage:
 1. `TracedRunnable.of(delegate)` which will capture the _active span_ when it is invoked to be re-activated 
    just before calling the delegate `Runnable` implementation,
    making sure to deactivate it again after the call has finished.
 2. `TracedCallable.of(delegate)` which will capture the _active span_ when it is invoked to be re-activated
    just before calling the delegate `Callable` implementation,
    making sure to deactivate it again after the call has finished.
 3. `TracedExecutorService.traced(delegate)` which will wrap a given `ExecutorService`
    and replace all scheduled `Runnable` or `Callable` tasks with their _Traced_
    variants just before scheduling them with the delegate.
 4. `TracedExecutors` utility-class with static factory methods comparable with the
    `Executors` utility class in the JVM, for example: `TracedExecutors.newCachedThreadPool()`.

## General overview
The relation between these global tracer concepts and the Opentracing API are represented in the following UML diagram for this package:  

<img src="http://plantuml.com/plantuml/png/bLLDJ-Cm4BttLupOIrQKIdkjK5TGYHK7u01_OE9CNIF7ZcAtMeJuxuppqOOqJXjVOetVyyoycN4000pcv0eK1CekR476boYKaGjXUQlIXTGsHGsVinaqIsXq3lvLzZT6Kycdq5cVpgDNuWkkIvBAUIgJ6lTx2NkhiWZOLvY4GJNw0Fiw-iRK1PZ_4SHhWUO93Sfu9B_QI1uDcuoWv8JAVaziaKWv0BMdW0lHXkyO43qb0SFGjRQECDKKmGjDMDuTRdVX-jHQjlOyj3fmPZL7wpEqZeZAi12RWPeFL_FewphTAPrLURzoSnXvgDKY6E_3QL-q3bkPTnA-qcBDtKN__HmUR9cZlrslvh0UR8yEdmbXb_GudViwsRQx7trwSxE2gpzNmtaleIZjNcNaPd7sIOiiOUUwZAYuWJCYhQWpI289zU4RPPJR9kSaQs3QLhKQXOhGcsSlTEJVK3RbdyEX3Kw784vgMdCYwtcNfzMKZfAqbSaJ5hSjw-_g4aRGPmxJUPUbV_HmiLptA2LtrwEdVABMSeWSIDLcpETBGIM_GzOyypzXGcfjV2zf6E-fOiHu_GdkFl9X40UcQmd7XxaUoERHs_9G0-M5_u2D8fsvlZKdlxMP-mO0">  
