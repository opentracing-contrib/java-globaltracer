package io.opentracing.contrib.global;

import io.opentracing.Span;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default ThreadLocal-based implementation of the {@link GlobalSpanManager} class that implements the following
 * stack unwinding algorithm:
 * <ol>
 * <li>If the closed <code>managed span</code> is <strong>not</strong> the active span,
 * </li>
 * </ol>
 *
 * @author Sjoerd Talsma
 */
class ThreadLocalSpanManager extends GlobalSpanManager {

    private static final Logger LOGGER = Logger.getLogger(GlobalSpanManager.class.getName());
    private static final ThreadLocal<ManagedSpan> ACTIVE = new ThreadLocal<ManagedSpan>();

    ThreadLocalSpanManager() {
    }

    public Span getActiveSpan() {
        final ManagedSpan activeSpan = ACTIVE.get();
        return activeSpan != null ? activeSpan.span : null;
    }

    public Closeable setActiveSpan(Span span) {
        final ManagedSpan managedSpan = new ManagedSpan(span);
        ACTIVE.set(managedSpan);
        return managedSpan;
    }

    public boolean clearAllActiveSpans() {
        final boolean cleared = ACTIVE.get() != null;
        ACTIVE.remove();
        return cleared;
    }

    private static class ManagedSpan implements Closeable {
        private final ManagedSpan parent = ACTIVE.get();
        private final Span span;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ManagedSpan(final Span span) {
            this.span = span;
        }

        public void close() {
            if (closed.compareAndSet(false, true)) {
                ManagedSpan current = ACTIVE.get();
                if (this == current) {
                    while (current != null && current.closed.get()) {
                        current = current.parent;
                    }
                    if (current == null) ACTIVE.remove();
                    else ACTIVE.set(current);
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

        @Override
        public String toString() {
            return closed.get() ? "ManagedSpan{closed}" : "ManagedSpan{" + span + '}';
        }
    }
}