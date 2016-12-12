package io.opentracing.contrib.global.delegation;

import io.opentracing.NoopSpanBuilder;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import java.util.Map;

/**
 * Abstract delegate {@link Tracer.SpanBuilder SpanBuilder} that can be extended by concrete implementations
 * to override individual methods.
 *
 * @author Sjoerd Talsma
 */
public abstract class DelegateSpanBuilder implements Tracer.SpanBuilder {

    /**
     * Non-<code>null</code> delegate span to forward all called methods to.
     */
    protected final Tracer.SpanBuilder delegate;

    public DelegateSpanBuilder(Tracer.SpanBuilder delegate) {
        this.delegate = delegate;
        if (delegate == null) throw new NullPointerException("Delegate SpanBuilder was <null>.");
    }

    private static boolean isNoop(Tracer.SpanBuilder builder) {
        return builder == null || builder instanceof NoopSpanBuilder;
    }

    public Tracer.SpanBuilder asChildOf(SpanContext parent) {
        return isNoop(delegate.asChildOf(parent)) ? NoopSpanBuilder.INSTANCE : this;
    }

    public Tracer.SpanBuilder asChildOf(Span parent) {
        return isNoop(delegate.asChildOf(parent)) ? NoopSpanBuilder.INSTANCE : this;
    }

    public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        return isNoop(delegate.addReference(referenceType, referencedContext)) ? NoopSpanBuilder.INSTANCE : this;
    }

    public Tracer.SpanBuilder withTag(String key, String value) {
        return isNoop(delegate.withTag(key, value)) ? NoopSpanBuilder.INSTANCE : this;
    }

    public Tracer.SpanBuilder withTag(String key, boolean value) {
        return isNoop(delegate.withTag(key, value)) ? NoopSpanBuilder.INSTANCE : this;
    }

    public Tracer.SpanBuilder withTag(String key, Number value) {
        return isNoop(delegate.withTag(key, value)) ? NoopSpanBuilder.INSTANCE : this;
    }

    public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
        return isNoop(delegate.withStartTimestamp(microseconds)) ? NoopSpanBuilder.INSTANCE : this;
    }

    public Span start() {
        return delegate.start();
    }

    public Iterable<Map.Entry<String, String>> baggageItems() {
        return delegate.baggageItems();
    }
}
