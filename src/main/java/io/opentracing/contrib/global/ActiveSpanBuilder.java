package io.opentracing.contrib.global;

import io.opentracing.NoopSpanBuilder;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;

import java.util.Map;

/**
 * {@link SpanBuilder} that forwards all methods to a delegate.<br>
 * Only the {@link #start()} method is overridden, {@link ActiveSpanManager#activate(Span) activating}
 * the started {@link Span} and wrapping it in an {@link ActiveSpan} object.<br>
 * The {@link ActiveSpan} object {@link ActiveSpanManager.SpanDeactivator deactivates} the span automatically
 * when it is {@link ActiveSpan#finish() finished} or {@link ActiveSpan#close() closed}.
 *
 * @author Sjoerd Talsma
 * @see ActiveSpanManager#activate(Span)
 * @see ActiveSpan#finish()
 */
final class ActiveSpanBuilder implements SpanBuilder {

    protected SpanBuilder delegate;

    ActiveSpanBuilder(SpanBuilder delegate) {
        if (delegate == null) throw new NullPointerException("Delegate SpanBuilder was <null>.");
        this.delegate = delegate;
    }

    /**
     * Replaces the {@link #delegate} SpanBuilder by an delegated-method result.
     * <p>
     * If the method result is <code>null</code> or a {@link NoopSpanBuilder},
     * the method short-circuits to the {@link NoopSpanBuilder#INSTANCE},
     * bypassing this ActiveSpanBuilder from further calls.
     *
     * @param spanBuilder The builder returned from the delegate (normally '== delegate').
     * @return Either this re-wrapped DelegateSpanBuilder or the NoopSpanBuilder.
     */
    SpanBuilder rewrap(SpanBuilder spanBuilder) {
        if (spanBuilder == null || spanBuilder instanceof NoopSpanBuilder) return NoopSpanBuilder.INSTANCE;
        this.delegate = spanBuilder;
        return this;
    }

    /**
     * Starts the built Span and {@link ActiveSpanManager#activate(Span) activates} it.
     *
     * @return a new 'active' Span that deactivates itself upon <em>finish</em> or <em>close</em> calls.
     * @see ActiveSpan#finish()
     * @see ActiveSpanManager#activate(Span)
     */
    @Override
    public Span start() {
        // Return a new 'active' span that deactivates itself again when finished.
        final Span newSpan = delegate.start();
        return new ActiveSpan(newSpan, ActiveSpanManager.activate(newSpan));
    }

    // All other methods are forwarded to the delegate SpanBuilder.

    public SpanBuilder asChildOf(SpanContext parent) {
        return rewrap(delegate.asChildOf(parent));
    }

    public SpanBuilder asChildOf(Span parent) {
        if (parent instanceof ActiveSpan) parent = ((ActiveSpan) parent).delegate;
        return rewrap(delegate.asChildOf(parent));
    }

    public SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        return rewrap(delegate.addReference(referenceType, referencedContext));
    }

    public SpanBuilder withTag(String key, String value) {
        return rewrap(delegate.withTag(key, value));
    }

    public SpanBuilder withTag(String key, boolean value) {
        return rewrap(delegate.withTag(key, value));
    }

    public SpanBuilder withTag(String key, Number value) {
        return rewrap(delegate.withTag(key, value));
    }

    public SpanBuilder withStartTimestamp(long microseconds) {
        return rewrap(delegate.withStartTimestamp(microseconds));
    }

    public Iterable<Map.Entry<String, String>> baggageItems() {
        return delegate.baggageItems();
    }

}
