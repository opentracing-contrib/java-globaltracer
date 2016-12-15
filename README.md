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

<img src="http://plantuml.com/plantuml/png/fLRBRjim4BpxAnRfHOLj0RqQXel4HOiSaW1Dq3j5hMaM52c8jDiYoB-Nf2IBqTDgTLelP_OnuguC0219XZed586hMEKeJK4eboocofeyYPbG2H7mkbX0zL11j8PltlzYqLZOKmYULss8uVGDbWKohWqMKOdxk87I680RVnXDmMozylONAJ3Q4o588d3xOsT9pxK_S4-6-7wIqc53VkqRApDj9VSLfzEPAtAGjcHsShurYfD4EvgffB-bXf_HLVj09673-d_GQEwb_IKfHXsMJTtn3HHDBhYeqcaCI0n63jEN87Rs5wnmQYXXYfT8ao5ichFYwK0aAf0KJGpj6aYKijvM3xN8LhOcks_vtoe8ufKr235HHl36kRHJSvWSotYojE1gN1E-VvwRLfR9YOlKg_9iwv3iCgi-yPqxRT-YWCtbJwJ6NuwzRGH_cnLyLKL6p7Q_My5-GiYb9JWhQd62U46tjVLkKwtNExZvTDFVzmRoGfrvYdenpEv8le6J3Xc3uZhEYBIqx20QQAWDFt39zJ6Qago22UNVrIYK2gLnCg3TAI-4LQz_1PThqDqAmwJgvizaFPwonA-jKP9GB7eW-RPcVTvTmt3wh60-xxhscmnNotL76BEtwzvWDbDhraTs96-CvNpAiR1hKyb7-wzWHcjrVK-96--vOWpncWdkVl3S8FPCrmQQBzLYXBvSh_ztiPRXgvrwj3i3CClD7pXm5AbkUpCqHvNglm00">  
