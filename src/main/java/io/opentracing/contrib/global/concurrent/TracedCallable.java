package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.contrib.global.GlobalSpanManager;

import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Callable} wrapper that will propagate the {@link GlobalSpanManager#activeSpan() active span} as global parent
 * span in the call that needs to be executed.
 *
 * @author Sjoerd Talsma
 */
public class TracedCallable<T> implements Callable<T> {
    private static final Logger LOGGER = Logger.getLogger(TracedCallable.class.getName());

    protected final Callable<T> delegate;
    protected final Span parentSpan;

    protected TracedCallable(Callable<T> delegate, Span parentSpan) {
        if (delegate == null) throw new NullPointerException("Callable delegate is <null>.");
        this.delegate = delegate;
        this.parentSpan = parentSpan;
    }

    /**
     * Creates a new traced callable that will execute with the currently
     * {@link GlobalSpanManager#activeSpan() active span} as active parent span in the new process.
     *
     * @param delegate The delegate callable to execute (required, non-<code>null</code>).
     * @param <T>      The result type of the call.
     * @return The traced callable that will propagate the currently active span to the new thread.
     * @see GlobalSpanManager#activeSpan()
     */
    public static <T> TracedCallable<T> of(Callable<T> delegate) {
        return new TracedCallable<T>(delegate, GlobalSpanManager.activeSpan());
    }

    /**
     * This method allows the caller to override the active span to use in the new thread.
     * <p>
     * <em>Please note:</em> it is <strong>not</strong> necessary to call this method with the
     * {@link GlobalSpanManager#activeSpan() current active span} as that is used {@link #of(Callable) by default}.
     *
     * @param parentSpan The span to use as active parent in the new thread.
     * @return A new callable object that will use the specified parent span.
     * @see #of(Callable)
     */
    public TracedCallable<T> withParent(Span parentSpan) {
        return new TracedCallable<T>(delegate, parentSpan);
    }

    /**
     * Performs the delegate call with the specified parent span.
     *
     * @return The result from the original call.
     * @throws Exception if the original call threw an exception.
     */
    public T call() throws Exception {
        final Closeable parentContext = activateParentSpan();
        try {
            return delegate.call();
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
