package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.contrib.global.ActiveSpanManager;
import io.opentracing.contrib.global.ActiveSpanManager.SpanDeactivator;

import java.util.logging.Logger;

import static io.opentracing.contrib.global.concurrent.TracedCallable.tryActivate;
import static io.opentracing.contrib.global.concurrent.TracedCallable.tryDeactivate;

/**
 * {@link Runnable} wrapper that will propagate the {@link ActiveSpanManager#activeSpan() active span} as global parent
 * span in the call that needs to be executed.
 *
 * @author Sjoerd Talsma
 */
public class TracedRunnable implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(TracedRunnable.class.getName());

    protected final Runnable delegate;
    private final Span parentSpan;

    protected TracedRunnable(Runnable delegate, Span parentSpan) {
        if (delegate == null) throw new NullPointerException("Runnable delegate is <null>.");
        this.delegate = delegate;
        this.parentSpan = parentSpan;
    }

    /**
     * Creates a new traced runnable that will execute with the currently
     * {@link ActiveSpanManager#activeSpan() active span} as active parent span in the new process.
     *
     * @param delegate The delegate runnable to execute (required, non-<code>null</code>).
     * @return The traced runnable that will propagate the currently active span to the new thread.
     * @see ActiveSpanManager#activeSpan()
     */
    public static TracedRunnable of(Runnable delegate) {
        return new TracedRunnable(delegate, ActiveSpanManager.activeSpan());
    }

    /**
     * This method allows the caller to override the active span to use in the new thread.
     * <p>
     * <em>Please note:</em> it is <strong>not</strong> necessary to call this method with the
     * {@link ActiveSpanManager#activeSpan() current active span} as that is used {@link #of(Runnable) by default}.
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
        SpanDeactivator deactivator = tryActivate(parentSpan);
        try {
            delegate.run();
        } finally {
            tryDeactivate(deactivator);
        }
    }

}
