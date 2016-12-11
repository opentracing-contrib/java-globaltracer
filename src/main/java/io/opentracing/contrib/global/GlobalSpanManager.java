package io.opentracing.contrib.global;

import io.opentracing.Span;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class manages the globally 'active' {@link Span} in the current Thread.
 *
 * @author Sjoerd Talsma
 */
public final class GlobalSpanManager {

    private static final Logger LOGGER = Logger.getLogger(GlobalSpanManager.class.getName());

    private static final ThreadLocal<ManagedSpan> ACTIVE_SPAN = new InheritableThreadLocal<ManagedSpan>();

    /**
     * Static method to return the currently active global {@link Span}.<br>
     * There is no guarantee that there is an active {@link Span} in all situations.
     *
     * @return The currently active global Span, or <code>null</code> if there is no Span currently active.
     */
    public static Span activeSpan() {
        final ManagedSpan active = ACTIVE_SPAN.get();
        return active != null ? active.span : null; // TODO: Or return no-op Span instead?
    }

    public static GlobalSpanDeactivator activate(Span span) {
        final ManagedSpan managed = new ManagedSpan(span);
        ACTIVE_SPAN.set(managed);
        return managed;
    }

    public static boolean clearActiveSpans() {
        final boolean cleared = ACTIVE_SPAN.get() != null;
        ACTIVE_SPAN.remove();
        return cleared;
    }

    private GlobalSpanManager() {
    }

    /**
     * Interface to deactivate an activated Global span with (to reactivate its parent span if possible).
     * <p>
     * The span will not be finished by this call.<br>
     * That responsibility lies with the creator of the span itself.
     */
    public interface GlobalSpanDeactivator extends Closeable {
        void close();
    }

    private static class ManagedSpan implements GlobalSpanDeactivator {

        private final ManagedSpan parent = ACTIVE_SPAN.get();
        private final Span span;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ManagedSpan(final Span span) {
            this.span = span;
        }

        public void close() {
            if (closed.compareAndSet(false, true)) {
                ManagedSpan current = ACTIVE_SPAN.get();
                if (this == current) {
                    while (current != null && current.closed.get()) {
                        current = current.parent;
                    }
                    if (current == null) ACTIVE_SPAN.remove();
                    else ACTIVE_SPAN.set(current);
                    LOGGER.log(Level.FINER, "Closed {0} and restored the current active span to {1}.",
                            new Object[]{this, current});
                } else {
                    LOGGER.log(Level.FINE, "Closed {0} without affecting the current active span which was {1}.",
                            new Object[]{this, current});
                }
            } else {
                LOGGER.log(Level.FINEST, "No action needed, {0} was already closed.", this);
            }
        }
    }
}
