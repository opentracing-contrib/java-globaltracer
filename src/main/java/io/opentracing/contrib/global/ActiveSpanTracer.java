package io.opentracing.contrib.global;

import io.opentracing.NoopTracer;
import io.opentracing.NoopTracerFactory;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * {@link Tracer} that forwards all methods to a delegate.<br>
 * Created {@link SpanBuilder} instances are wrapped in {@link ActiveSpanBuilder} wrappers.
 */
final class ActiveSpanTracer implements Tracer {

    protected Tracer delegate;

    ActiveSpanTracer(Tracer delegate) {
        if (delegate == null) throw new NullPointerException("The delegate Tracer implementation is <null>.");
        this.delegate = delegate;
    }

    static Tracer wrap(Tracer delegate) {
        if (delegate == null || delegate instanceof NoopTracer) return NoopTracerFactory.create();
        return new ActiveSpanTracer(delegate);
    }

    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        if (spanContext instanceof ActiveSpanBuilder) { // Weird that Builder extends Context!
            spanContext = ((ActiveSpanBuilder) spanContext).delegate;
        }
        delegate.inject(spanContext, format, carrier);
    }

    public <C> SpanContext extract(Format<C> format, C carrier) {
        return delegate.extract(format, carrier);
    }

    public SpanBuilder buildSpan(String operationName) {
        return new ActiveSpanBuilder(delegate.buildSpan(operationName));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{delegate=" + delegate + '}';
    }

}
