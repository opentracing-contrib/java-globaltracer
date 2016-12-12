package io.opentracing.contrib.global;

import io.opentracing.Span;

import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class manages the globally <em>active</em> {@link Span}.<br>
 * The default implementation will use a {@link ThreadLocal ThreadLocal storage} to maintain the active {@link Span}.
 * <p>
 * Each {@link Span} implementation obtained from the {@link GlobalTracer#tracer() global tracer} will automatically
 * become the <em>active</em> {@link Span} until it is {@link Span#finish() finished} or {@link Span#close() closed}.<br>
 * As a result, {@link io.opentracing.Tracer Tracer} clients need not worry about interacting with this
 * {@link GlobalSpanManager} explicitly.
 * <p>
 * Providers of {@link io.opentracing.Tracer Tracer} implementations that want to customize the global Span management
 * can provide their own
 *
 * @author Sjoerd Talsma
 * @navassoc - activeSpan - io.opentracing.Span
 */
public abstract class GlobalSpanManager {
    private static final Logger LOGGER = Logger.getLogger(GlobalSpanManager.class.getName());

    /**
     * Overridable singleton instance of the global span manager.
     */
    private static final AtomicReference<GlobalSpanManager> INSTANCE = new AtomicReference<GlobalSpanManager>();

    private static final Callable<GlobalSpanManager> DEFAULT_PROVIDER = new Callable<GlobalSpanManager>() {
        public GlobalSpanManager call() throws Exception {
            return new ThreadLocalSpanManager();
        }
    };

    private static GlobalSpanManager getInstance() {
        GlobalSpanManager instance = INSTANCE.get();
        if (instance == null) {
            final GlobalSpanManager singleton = ServiceLoader.loadSingleton(GlobalSpanManager.class, DEFAULT_PROVIDER);
            while (instance == null && singleton != null) {
                INSTANCE.compareAndSet(null, singleton);
                instance = INSTANCE.get();
            }
            LOGGER.log(Level.FINE, "Singleton GlobalSpanManager implementation: {0}.", instance);
        }
        return instance;
    }

    /**
     * This method allows explicit registration of a configured <code>GlobalSpanManager</code> implementation
     * to override the behaviour of the default <code>ThreadLocal</code> implementation.
     *
     * @param instance The overridden implementation to use for in-process span management.
     * @return The previous <code>GlobalSpanManager</code> that was initialized before,
     * or <code>null</code> if there was no previous instance active.
     */
    protected static GlobalSpanManager registerInstance(GlobalSpanManager instance) {
        return INSTANCE.getAndSet(instance);
    }

    /**
     * Static method to return the currently active global {@link Span}.<br>
     * There is no guarantee that there is an active {@link Span} in all situations.
     *
     * @return The currently active global Span, or <code>null</code> if there is no Span currently active.
     */
    public static Span activeSpan() {
        return getInstance().getActiveSpan();
    }

    /**
     * This method makes the specified span the <em>globally active</em> span within the global span manager.
     * <p>
     * It is left to the actual implementation how to deal with <code>null</code> spans.
     * The default <code>ThreadLocal</code>-based implementation temporarily <em>suspends</em> the span activation,
     * meaning no {@link #activeSpan()} is returned until the {@link Closeable} is closed.
     *
     * @param span The span to become the globally active span.
     * @return The object that will return the <em>active</em> span back to the current state when closed.
     * @see #activeSpan()
     */
    public static Closeable activate(Span span) {
        return getInstance().setActiveSpan(span);
    }

    /**
     * This method clears <em>all</em> active spans if they exist.
     * <p>
     * For in-process stack-based global span manager implementations, this method should clear the entire stack
     * for the current process.
     * <p>
     * This method exists to allow boundary filters a failsafe way to clear any unclosed active spans before finishing
     * and makes sure that any Threads that are returned to thread pools can be cleaned before they get re-used.
     *
     * @return <code>true</code> if there were active spans that were cleared,
     * or <code>false</code> if there were no active spans left.
     */
    public static boolean clearActiveSpans() {
        return getInstance().clearAllActiveSpans();
    }

    // The abstract methods to be implemented by the span manager.

    protected abstract Span getActiveSpan();

    protected abstract Closeable setActiveSpan(Span span);

    protected abstract boolean clearAllActiveSpans();

}
