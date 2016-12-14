package io.opentracing.contrib.global;

import io.opentracing.Span;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default ThreadLocal-based implementation of the {@link ActiveSpanManager} class that implements the following
 * stack unwinding algorithm:
 * <ol>
 * <li>If the closed <code>managed span</code> is not the active span, the active span is left alone.</li>
 * <li>Otherwise, the first parent that is <em>not yet closed</em> is set as the new active span.</li>
 * <li>If no unclosed parents are available, the active span is cleared.</li>
 * <li>Consecutive <code>close()</code> calls will be ignored.</li>
 * </ol>
 *
 * @author Sjoerd Talsma
 */
final class ThreadLocalSpanManager extends ActiveSpanManager {

    private static final Logger LOGGER = Logger.getLogger(ActiveSpanManager.class.getName());
    private static final ThreadLocal<ManagedSpan> ACTIVE = new ThreadLocal<ManagedSpan>();

    ThreadLocalSpanManager() {
    }

    public Span getActiveSpan() {
        final ManagedSpan activeSpan = ACTIVE.get();
        return activeSpan != null ? activeSpan.span : null;
    }

    public SpanDeactivator setActiveSpan(Span span) {
        final ManagedSpan managedSpan = new ManagedSpan(span);
        ACTIVE.set(managedSpan);
        return managedSpan;
    }

    public boolean clearAllActiveSpans() {
        final boolean cleared = ACTIVE.get() != null;
        ACTIVE.remove();
        return cleared;
    }

    private static class ManagedSpan implements SpanDeactivator {
        private final ManagedSpan parent = ACTIVE.get();
        private final Span span;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ManagedSpan(final Span span) {
            this.span = span;
        }

        public void deactivate() {
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
