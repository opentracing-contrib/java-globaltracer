package io.opentracing.contrib.global.delegation;

import io.opentracing.NoopSpanBuilder;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import java.util.Map;

/**
 * {@link Tracer.SpanBuilder SpanBuilder} that forwards all methods to a delegate.
 *
 * @author Sjoerd Talsma
 */
abstract class ForwardingSpanBuilder extends ForwardingSpanContext implements Tracer.SpanBuilder {

    /**
     * Non-<code>null</code> delegate span to forward all called methods to.
     */
    protected Tracer.SpanBuilder delegate;

    ForwardingSpanBuilder(Tracer.SpanBuilder delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    /**
     * Deals with 'rewrapping' the intermediate <code>spanBuilder</code> from the delegate.
     * <p>
     * If the delegate returns <code>null</code> or a {@link NoopSpanBuilder}, this wrapping spanbuilder is replaced
     * by the {@link NoopSpanBuilder#INSTANCE}.
     *
     * @param spanBuilder The builder returned from the delegate (normally '== delegate').
     * @return Either this re-wrapped DelegateSpanBuilder or the NoopSpanBuilder.
     */
    Tracer.SpanBuilder rewrap(Tracer.SpanBuilder spanBuilder) {
        if (spanBuilder == null || spanBuilder instanceof NoopSpanBuilder) return NoopSpanBuilder.INSTANCE;
        this.delegate = spanBuilder;
        return this;
    }

    public Tracer.SpanBuilder asChildOf(SpanContext parent) {
        return rewrap(delegate.asChildOf(parent));
    }

    public Tracer.SpanBuilder asChildOf(Span parent) {
        return rewrap(delegate.asChildOf(parent));
    }

    public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        return rewrap(delegate.addReference(referenceType, referencedContext));
    }

    public Tracer.SpanBuilder withTag(String key, String value) {
        return rewrap(delegate.withTag(key, value));
    }

    public Tracer.SpanBuilder withTag(String key, boolean value) {
        return rewrap(delegate.withTag(key, value));
    }

    public Tracer.SpanBuilder withTag(String key, Number value) {
        return rewrap(delegate.withTag(key, value));
    }

    public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
        return rewrap(delegate.withStartTimestamp(microseconds));
    }

    public Span start() {
        return delegate.start();
    }

    public Iterable<Map.Entry<String, String>> baggageItems() {
        return delegate.baggageItems();
    }
}
