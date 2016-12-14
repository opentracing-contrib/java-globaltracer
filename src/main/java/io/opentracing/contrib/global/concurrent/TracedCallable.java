package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.contrib.global.ActiveSpanManager;
import io.opentracing.contrib.global.ActiveSpanManager.SpanDeactivator;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Callable} wrapper that will propagate the {@link ActiveSpanManager#activeSpan() active span} as global parent
 * span in the call that needs to be executed.
 *
 * @author Sjoerd Talsma
 */
public class TracedCallable<T> implements Callable<T> {
    private static final Logger LOGGER = Logger.getLogger(TracedCallable.class.getName());

    protected final Callable<T> delegate;
    private final Span parentSpan;

    protected TracedCallable(Callable<T> delegate, Span parentSpan) {
        if (delegate == null) throw new NullPointerException("Callable delegate is <null>.");
        this.delegate = delegate;
        this.parentSpan = parentSpan;
    }

    /**
     * Creates a new traced callable that will execute with the currently
     * {@link ActiveSpanManager#activeSpan() active span} as active parent span in the new process.
     *
     * @param delegate The delegate callable to execute (required, non-<code>null</code>).
     * @param <T>      The result type of the call.
     * @return The traced callable that will propagate the currently active span to the new thread.
     * @see ActiveSpanManager#activeSpan()
     */
    public static <T> TracedCallable<T> of(Callable<T> delegate) {
        return new TracedCallable<T>(delegate, ActiveSpanManager.activeSpan());
    }

    /**
     * This method allows the caller to override the active span to use in the new thread.
     * <p>
     * <em>Please note:</em> it is <strong>not</strong> necessary to call this method with the
     * {@link ActiveSpanManager#activeSpan() current active span} as that is used {@link #of(Callable) by default}.
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
        final SpanDeactivator parentContext = tryActivate(parentSpan);
        try {
            return delegate.call();
        } finally {
            tryDeactivate(parentContext);
        }
    }

    static SpanDeactivator tryActivate(Span span) {
        try {
            return ActiveSpanManager.activate(span);
        } catch (RuntimeException activationException) {
            LOGGER.log(Level.WARNING, "Could not activate span {0}.", new Object[]{span, activationException});
            return null;
        }
    }

    static void tryDeactivate(SpanDeactivator deactivator) {
        if (deactivator != null) try {
            deactivator.deactivate();
        } catch (Exception deactivationException) {
            LOGGER.log(Level.WARNING, "Could not deactivate {0}.", new Object[]{deactivator, deactivationException});
        }
    }

}
