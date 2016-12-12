package io.opentracing.contrib.global.delegation;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * Abstract delegate {@link Tracer Tracer} that can be extended by concrete implementations
 * to override individual methods.
 *
 * @author Sjoerd Talsma
 */
public abstract class DelegateTracer implements Tracer {

    /**
     * Non-<code>null</code> delegate tracer to forward all called methods to.
     */
    protected final Tracer delegate;

    public DelegateTracer(Tracer delegate) {
        if (delegate == null) throw new NullPointerException("The delegate Tracer implementation is <null>.");
        this.delegate = delegate;
    }

    public SpanBuilder buildSpan(String operationName) {
        return delegate.buildSpan(operationName);
    }

    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        delegate.inject(spanContext, format, carrier);
    }

    public <C> SpanContext extract(Format<C> format, C carrier) {
        return delegate.extract(format, carrier);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{delegate=" + delegate + '}';
    }

}
