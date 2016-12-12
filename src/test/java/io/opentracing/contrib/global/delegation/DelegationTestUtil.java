package io.opentracing.contrib.global.delegation;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;

/**
 * Test utility that allows unwrapping the delegates from wrapped objects.
 *
 * @author Sjoerd Talsma
 */
public class DelegationTestUtil {

    public static Span unwrap(Span span) {
        return span instanceof DelegateSpan ? ((DelegateSpan) span).delegate : span;
    }

    public static SpanBuilder unwrap(SpanBuilder spanBuilder) {
        return spanBuilder instanceof DelegateSpanBuilder ? ((DelegateSpanBuilder) spanBuilder).delegate : spanBuilder;
    }

    public static Tracer unwrap(final Tracer tracer) {
        return tracer instanceof DelegateTracer ? ((DelegateTracer) tracer).delegate : tracer;
    }

}
