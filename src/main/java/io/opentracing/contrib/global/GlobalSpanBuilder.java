package io.opentracing.contrib.global;

import io.opentracing.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper implementation that delegates all regular {@link Tracer.SpanBuilder} methods to the specified
 * delegate builder instead.<br>
 * Only the {@link #start()} method is overridden, starting and registering a new {@link GlobalSpan} wrapping the
 * {@link Span} created by the delegate builder.
 * <p>
 * The {@link GlobalTracer#activeSpan() currently active global Span} will be registered as parent, right before
 * {@link #start() starting} the new span.<br>
 * There is one exception: Explicit {@link #asChildOf(SpanContext) parent spans} are respected.<br>
 * In this case any currently active global {@link Span} will <strong>not</strong> be registered as parent
 * for the new span.
 *
 * @author Sjoerd Talsma
 */
class GlobalSpanBuilder implements Tracer.SpanBuilder {
    private final Tracer.SpanBuilder delegate;
    private final AtomicBoolean explicitParent = new AtomicBoolean(false);

    GlobalSpanBuilder(Tracer.SpanBuilder delegate) {
        this.delegate = delegate;
        if (delegate == null) throw new NullPointerException("Delegate SpanBuilder is <null>.");
    }

    public Tracer.SpanBuilder asChildOf(SpanContext parent) {
        if (parent == null || parent instanceof NoopSpanContext) {
            final Span activeSpan = GlobalTracer.activeSpan();
            return activeSpan != null ? this : NoopSpanBuilder.INSTANCE;
        }
        delegate.asChildOf(parent);
        explicitParent.set(true);
        return this;
    }

    public Span start() {
        // TODO: Think about this condition; OpenTracing spec allows multiple parent spans.
        // Would we want that in this case?
        if (!explicitParent.get()) {
            final Span activeSpan = GlobalTracer.activeSpan();
            if (activeSpan != null) delegate.asChildOf(activeSpan.context());
        }
        return new GlobalSpan(delegate.start(), true);
    }

    public Tracer.SpanBuilder asChildOf(Span parent) {
        // Re-use CHILD_OF logic from the 'main' asChildOf method.
        return asChildOf(parent != null ? parent.context() : null);
    }

    public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        // Re-use CHILD_OF logic from the 'main' asChildOf method.
        if (References.CHILD_OF.equals(referenceType)) return asChildOf(referencedContext);

        delegate.addReference(referenceType, referencedContext);
        return this;
    }

    public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
        delegate.withStartTimestamp(microseconds);
        return this;
    }

    public Tracer.SpanBuilder withTag(String key, String value) {
        delegate.withTag(key, value);
        return this;
    }

    public Tracer.SpanBuilder withTag(String key, boolean value) {
        delegate.withTag(key, value);
        return this;
    }

    public Tracer.SpanBuilder withTag(String key, Number value) {
        delegate.withTag(key, value);
        return this;
    }

    public Iterable<Map.Entry<String, String>> baggageItems() {
        return delegate.baggageItems();
    }

}
