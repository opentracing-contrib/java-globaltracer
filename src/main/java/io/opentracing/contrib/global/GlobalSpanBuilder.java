package io.opentracing.contrib.global;

import io.opentracing.NoopSpanContext;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.global.delegation.ForwardingSpan;
import io.opentracing.contrib.global.delegation.ForwardingSpanBuilder;

import java.io.Closeable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SpanBuilder implementation that delegates all regular {@link Tracer.SpanBuilder} methods to the specified
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
class GlobalSpanBuilder extends ForwardingSpanBuilder {

    GlobalSpanBuilder(Tracer.SpanBuilder delegate) {
        super(delegate);
    }

    /**
     * Obtains the {@link GlobalSpanManager#activeSpan() active global span} and returns its
     * {@link Span#context() context}, except when this context is the no-op SpanContext,
     * in which case <code>null</code> is returned.
     *
     * @return The active SpanContext or <code>null</code> if there is no active SpanContext available (or is no-op).
     * @see NoopSpanContext
     */
    private static SpanContext activeSpanContext() {
        final Span activeSpan = GlobalSpanManager.activeSpan();
        final SpanContext activeSpanContext = activeSpan != null ? activeSpan.context() : null;
        return activeSpanContext instanceof NoopSpanContext ? null : activeSpanContext;
    }

    @Override
    public Span start() {
        // Tell the delegate builder that the new Span should be a 'child of' the active span context.
        final SpanContext activeContext = activeSpanContext();
        if (activeContext != null) delegate.asChildOf(activeContext);

        // Return a new 'active' span that deactivates itself again when finished.
        final Span newSpan = delegate.start();
        return new ActiveSpan(newSpan, GlobalSpanManager.activate(newSpan));
    }

    /**
     * Implementation of an 'active span'.<br>
     * This active span will deactivate itself after it has been finished,
     * otherwise delegating al span functionality to the underlying Tracer implementation.
     */
    private static final class ActiveSpan extends ForwardingSpan {
        private static final Logger LOGGER = Logger.getLogger(ActiveSpan.class.getName());
        private volatile Closeable deactivator;

        private ActiveSpan(Span delegate, Closeable deactivator) {
            super(delegate);
            this.deactivator = deactivator;
        }

        private void deactivate() {
            if (this.deactivator != null) try {
                this.deactivator.close();
                this.deactivator = null;
            } catch (Exception deactivationException) {
                LOGGER.log(Level.WARNING, "Exception deactivating {0}.", new Object[]{this, deactivationException});
            }
        }

        public void finish() {
            try {
                super.finish();
            } finally {
                this.deactivate();
            }
        }

        public void finish(long finishMicros) {
            try {
                super.finish(finishMicros);
            } finally {
                this.deactivate();
            }
        }

        public void close() {
            try {
                super.close();
            } finally {
                this.deactivate();
            }
        }
    }

}
