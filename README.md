# java-globaltracer
Global Tracer, forwarding to another Tracer implementation.

This library provides two utility classes `GlobalTracer` and `ActiveSpanManager`:

## GlobalTracer
This class has the following purpose:
 1. The `GlobalTracer.tracer()` factory method returning the singleton _global tracer_.
    If there is no global tracer, a `NoopTracer` is returned instead.
 2. Enrich the lifecycle of created `Span` objects through the global Tracer 
    to update the _active span_ as they are started and finished.
 3. Utility `spanAware()` methods to create 'span aware' Runnable and Callable instances
    that run with the _active span_ from the scheduling thread.
 4. Utility `traced()` methods to create Runnable and Callable instances 
    that run within a new Span that is _child of the active span_ form the scheduling thread.
    An `operationName` must be provided for a new Span to be created.

## ActiveSpanManager
This class:
 1. Provides clients with the `ActiveSpanManger.activeSpan()` method to return the
    _active span_.
    If there is no active span, the method never returns a `null` refrence, but
    a `NoopSpan` instead.
 2. Modifications to the _active span_ can be made through the `activate` 
    and `deactivate` methods.
    Note that Spans created through the GlobalTracer.tracer() instance will 
    activate and deactivate themselves.

## How to use this library

  _TODO: rewrite the examples!_

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
