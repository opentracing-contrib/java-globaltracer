package io.opentracing.contrib.global;

import io.opentracing.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

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
        this.delegate = requireNonNull(delegate, "Delegate SpanBuilder is <null>.");
    }

    @Override
    public Tracer.SpanBuilder asChildOf(SpanContext parent) {
        if (parent == null || parent instanceof NoopSpanContext) {
            final Optional<Span> activeSpan = GlobalTracer.activeSpan();
            return activeSpan.isPresent() ? this : NoopSpanBuilder.INSTANCE;
        }
        delegate.asChildOf(parent);
        explicitParent.set(true);
        return this;
    }

    @Override
    public Span start() {
        if (!explicitParent.get()) {
            final Optional<Span> activeSpan = GlobalTracer.activeSpan();
            if (activeSpan.isPresent()) delegate.asChildOf(activeSpan.get().context());
        }
        return new GlobalSpan(delegate.start());
    }

    @Override
    public Tracer.SpanBuilder asChildOf(Span parent) {
        return asChildOf(parent != null ? parent.context() : null); // Re-use CHILD_OF logic.
    }

    @Override
    public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        if (References.CHILD_OF.equals(referenceType)) return asChildOf(referencedContext);
        delegate.addReference(referenceType, referencedContext);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
        delegate.withStartTimestamp(microseconds);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, String value) {
        delegate.withTag(key, value);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, boolean value) {
        delegate.withTag(key, value);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, Number value) {
        delegate.withTag(key, value);
        return this;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return delegate.baggageItems();
    }

}
