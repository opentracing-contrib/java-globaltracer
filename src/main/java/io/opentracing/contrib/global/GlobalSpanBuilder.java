package io.opentracing.contrib.global;

import io.opentracing.*;

import java.util.Map;

/**
 * Wrapper implementation that delegates all regular {@link Tracer.SpanBuilder} methods to the specified
 * delegate builder instead.<br>
 * Only the {@link #start()} method is overridden,
 * starting and {@link GlobalSpanManager#activate(Span) activating} a new global {@link Span} wrapping the
 * {@link Span} created by the delegate builder to restore the parent span when finishing.
 * <p>
 * The {@link GlobalSpanManager#activeSpan() current globally active Span} will be registered as implicit parent,
 * right before {@link #start() starting} the new span.<br>
 *
 * @author Sjoerd Talsma
 */
class GlobalSpanBuilder implements Tracer.SpanBuilder {
    private final Tracer.SpanBuilder delegate;

    GlobalSpanBuilder(Tracer.SpanBuilder delegate) {
        this.delegate = delegate;
        if (delegate == null) throw new NullPointerException("Delegate SpanBuilder is <null>.");
    }

    /**
     * Obtains the {@link GlobalSpanManager#activeSpan() active global span} and returns it,
     * except when it's <code>null</code> or the no-op span, in which case no span (<code>null</code>) is returned.
     *
     * @return The active span or <code>null</code> if there is no active span implementation available.
     */
    private static Span activeGlobalSpan() {
        final Span active = GlobalSpanManager.activeSpan();
        return active == null || active instanceof NoopSpan ? null : active;
    }

    private static SpanContext activeGlobalSpanContext() {
        final Span activeGlobalSpan = activeGlobalSpan();
        return activeGlobalSpan != null ? activeGlobalSpan.context() : null;
    }

    public Tracer.SpanBuilder asChildOf(SpanContext parent) {
        if (parent == null || parent instanceof NoopSpanContext) {
            return activeGlobalSpan() != null ? this : NoopSpanBuilder.INSTANCE;
        }
        delegate.asChildOf(parent);
        return this;
    }

    public Span start() {
        final SpanContext activeContext = activeGlobalSpanContext();
        if (activeContext != null) delegate.asChildOf(activeContext);
        final Span newActiveSpan = delegate.start();
        final GlobalSpanManager.GlobalSpanDeactivator deactivator = GlobalSpanManager.activate(newActiveSpan);
        // TODO refactor to inner-class, first get unit-test functioning again!
        return new DelegateSpan(newActiveSpan) {
            public void finish() {
                try {
                    super.finish();
                } finally {
                    deactivator.close();
                }
            }

            public void finish(long finishMicros) {
                try {
                    super.finish(finishMicros);
                } finally {
                    deactivator.close();
                }
            }

            public void close() {
                try {
                    super.close();
                } finally {
                    deactivator.close();
                }
            }
        };
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
