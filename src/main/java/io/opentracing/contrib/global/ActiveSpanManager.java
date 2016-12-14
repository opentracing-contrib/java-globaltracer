package io.opentracing.contrib.global;

import io.opentracing.Span;

import java.io.Closeable;
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
        /**
         * Deactivates the {@link #activate(Span) active Span} this object was returned for.
         */
        void deactivate();
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
    public static SpanDeactivator activate(Span span) {
        return getInstance().setActiveSpan(span);
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
        return getInstance().clearAllActiveSpans();
    }

    // The abstract methods to be implemented by the span manager. // TODO JavaDoc

    protected abstract Span getActiveSpan();

    protected abstract SpanDeactivator setActiveSpan(Span span);

    protected abstract boolean clearAllActiveSpans();

}
