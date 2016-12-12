package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.contrib.global.GlobalSpanManager;

import java.io.Closeable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Runnable} wrapper that will propagate the {@link GlobalSpanManager#activeSpan() active span} as global parent
 * span in the call that needs to be executed.
 *
 * @author Sjoerd Talsma
 */
public class TracedRunnable implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(TracedRunnable.class.getName());

    protected final Runnable delegate;
    protected final Span parentSpan;

    protected TracedRunnable(Runnable delegate, Span parentSpan) {
        if (delegate == null) throw new NullPointerException("Runnable delegate is <null>.");
        this.delegate = delegate;
        this.parentSpan = parentSpan;
    }

    /**
     * Creates a new traced runnable that will execute with the currently
     * {@link GlobalSpanManager#activeSpan() active span} as active parent span in the new process.
     *
     * @param delegate The delegate runnable to execute (required, non-<code>null</code>).
     * @return The traced runnable that will propagate the currently active span to the new thread.
     * @see GlobalSpanManager#activeSpan()
     */
    public static TracedRunnable of(Runnable delegate) {
        return new TracedRunnable(delegate, GlobalSpanManager.activeSpan());
    }

    /**
     * This method allows the caller to override the active span to use in the new thread.
     * <p>
     * <em>Please note:</em> it is <strong>not</strong> necessary to call this method with the
     * {@link GlobalSpanManager#activeSpan() current active span} as that is used {@link #of(Runnable) by default}.
     *
     * @param parentSpan The span to use as active parent in the new thread.
     * @return A new runnable object that will use the specified parent span.
     * @see #of(Runnable)
     */
    public TracedRunnable withParent(Span parentSpan) {
        return new TracedRunnable(delegate, parentSpan);
    }

    /**
     * Performs the runnable action with the specified parent span.
     */
    public void run() {
        final Closeable parentContext = activateParentSpan();
        try {
            delegate.run();
        } finally {
            deactivate(parentContext);
        }
    }

    /**
     * Exception-handled invocation of {@link GlobalSpanManager#activate(Span)}.
     *
     * @return The resulting parent 'context' or <code>null</code> in case of activation exceptions.
     */
    private Closeable activateParentSpan() {
        try {
            return GlobalSpanManager.activate(parentSpan);
        } catch (RuntimeException activationException) {
            LOGGER.log(Level.WARNING, "Could not activate parent span {0}.",
                    new Object[]{parentSpan, activationException});
        }
        return null;
    }

    /**
     * Exception-handled invocation of {@link Closeable#close()}.
     *
     * @param parentContext The parent context to be closed or <code>null</code> in case of activation exceptions.
     */
    private void deactivate(Closeable parentContext) {
        if (parentContext != null) try {
            parentContext.close();
        } catch (Exception deactivationException) {
            LOGGER.log(Level.WARNING, "Could not deactivate parent span {0}.",
                    new Object[]{parentSpan, deactivationException});
        }
    }

}
