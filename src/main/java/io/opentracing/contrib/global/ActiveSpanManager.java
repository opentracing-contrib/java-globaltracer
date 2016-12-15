package io.opentracing.contrib.global;

import io.opentracing.NoopSpan;
import io.opentracing.Span;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the <em>active</em> {@link Span}.<br>
 * A {@link Span} becomes active in the current process after a call to {@link #activate(Span)}.
 * <p>
 * The default implementation will use a {@link ThreadLocal ThreadLocal storage} to maintain the active {@link Span}.
 * <p>
 * Each {@link Span} implementation obtained from the {@link GlobalTracer#tracer() global tracer} will automatically
 * become the <em>active</em> {@link Span} until it is {@link Span#finish() finished} or {@link Span#close() closed}.<br>
 * As a result, {@link io.opentracing.Tracer Tracer} clients need not worry about interacting with this
 * {@link ActiveSpanManager} explicitly.
 * <p>
 * Providers of {@link io.opentracing.Tracer Tracer} implementations that want to customize the active Span management
 * can provide their own implementation by either:
 * <ol>
 * <li>calling {@link #setInstance(ActiveSpanManager)} programmatically, or</li>
 * <li>defining a <code>META-INF/services/io.opentracing.contrib.global.ActiveSpanManager</code> service file
 * containing the classname of the implementation</li>
 * </ol>
 *
 * @author Sjoerd Talsma
 * @navassoc - activeSpan - io.opentracing.Span
 */
public abstract class ActiveSpanManager {
    private static final Logger LOGGER = Logger.getLogger(ActiveSpanManager.class.getName());

    /**
     * Interface to deactivate an active {@link Span} with.
     */
    public interface SpanDeactivator {
    }

    /**
     * Overridable singleton instance of the global span manager.
     */
    private static final AtomicReference<ActiveSpanManager> INSTANCE = new AtomicReference<ActiveSpanManager>();

    private static final Callable<ActiveSpanManager> DEFAULT_PROVIDER = new Callable<ActiveSpanManager>() {
        public ActiveSpanManager call() throws Exception {
            return new ThreadLocalSpanManager();
        }
    };

    private static ActiveSpanManager getInstance() {
        ActiveSpanManager instance = INSTANCE.get();
        if (instance == null) {
            final ActiveSpanManager singleton = SingletonServiceLoader.loadSingleton(ActiveSpanManager.class, DEFAULT_PROVIDER);
            while (instance == null && singleton != null) {
                INSTANCE.compareAndSet(null, singleton);
                instance = INSTANCE.get();
            }
            LOGGER.log(Level.FINE, "Singleton ActiveSpanManager implementation: {0}.", instance);
        }
        return instance;
    }

    /**
     * This method allows explicit registration of a configured <code>ActiveSpanManager</code> implementation
     * to override the behaviour of the default <code>ThreadLocal</code> implementation.
     * <p>
     * The previously active span manager is returned so it can be restored if necessary.
     *
     * @param instance The overridden implementation to use for in-process span management.
     * @return The previous <code>ActiveSpanManager</code> that was initialized before.
     */
    protected static ActiveSpanManager setInstance(ActiveSpanManager instance) {
        return INSTANCE.getAndSet(instance);
    }

    /**
     * Return the active {@link Span}.
     *
     * @return The active Span, or the <code>NoopSpan</code> if there is no active span.
     */
    public static Span activeSpan() {
        try {
            Span activeSpan = getInstance().getActiveSpan();
            if (activeSpan != null) return activeSpan;
        } catch (Exception activeSpanException) {
            LOGGER.log(Level.WARNING, "Could not obtain active span.", activeSpanException);
        }
        return NoopSpan.INSTANCE;
    }

    /**
     * Makes span the <em>active span</em> within the running process.
     * <p>
     * Any exception thrown by the {@link #setActiveSpan(Span) implementation} is logged and will return
     * no {@link SpanDeactivator} (<code>null</code>) because tracing code must not break application functionality.
     *
     * @param span The span to become the active span.
     * @return The object that will restore any currently <em>active</em> deactivated.
     * @see #activeSpan()
     * @see #deactivate(SpanDeactivator)
     */
    public static SpanDeactivator activate(Span span) {
        try {
            if (span == null) span = NoopSpan.INSTANCE;
            return getInstance().setActiveSpan(span);
        } catch (Exception activationException) {
            LOGGER.log(Level.WARNING, "Could not activate {0}.", new Object[]{span, activationException});
            return null;
        }
    }

    /**
     * Invokes the given {@link SpanDeactivator} which should normally reactivate the parent of the <em>active span</em>
     * within the running process.
     * <p>
     * Any exception thrown by the implementation is logged and swallowed because tracing code must not break
     * application functionality.
     *
     * @param deactivator The deactivator that was received upon span activation.
     * @see #activate(Span)
     */
    public static void deactivate(SpanDeactivator deactivator) {
        if (deactivator != null) try {
            getInstance().deactivateSpan(deactivator);
        } catch (Exception deactivationException) {
            LOGGER.log(Level.WARNING, "Could not deactivate {0}.", new Object[]{deactivator, deactivationException});
        }
    }

    /**
     * Clears any active spans.
     * <p>
     * This method allows boundary filters to clear any unclosed active spans before returning the Thread back to
     * the threadpool.
     *
     * @return <code>true</code> if there were active spans that were cleared,
     * or <code>false</code> if there were no active spans left.
     */
    public static boolean clearActiveSpans() {
        try {
            return getInstance().clearAllActiveSpans();
        } catch (Exception clearException) {
            LOGGER.log(Level.WARNING, "Could not clear active spans.", clearException);
            return false;
        }
    }

    // The abstract methods to be implemented by the span manager.
    // TODO JavaDoc

    protected abstract Span getActiveSpan();

    protected abstract SpanDeactivator setActiveSpan(Span span);

    protected abstract void deactivateSpan(SpanDeactivator deactivator);

    protected abstract boolean clearAllActiveSpans();

}
