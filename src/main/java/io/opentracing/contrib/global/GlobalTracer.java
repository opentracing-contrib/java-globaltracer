package io.opentracing.contrib.global;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The <code>GlobalTracer</code> class is not a {@link Tracer} implementation of itself, but instead a utility-class
 * to centrally reference the {@link #tracer() singleton instance} of the configured {@link Tracer} implementation.
 * <p>
 * The singleton instance can be instantiated in one of two ways:
 * <ol>
 * <li>Explicitly, by calling {@link #register(Tracer)} with a configured tracer implementation, or:</li>
 * <li>Automatically by using the Java <code>ServiceLoader</code> SPI mechanism to load an implementation
 * from the classpath.</li>
 * </ol>
 *
 * @author Sjoerd Talsma
 * @navassoc - provides 1 io.opentracing.Tracer
 * @navassoc - uses - ActiveSpanManager
 * @see Tracer
 */
public final class GlobalTracer {
    private static final Logger LOGGER = Logger.getLogger(GlobalTracer.class.getName());
    private static final Callable<Tracer> DEFAULT_PROVIDER = new Callable<Tracer>() {
        public Tracer call() throws Exception {
            return NoopTracerFactory.create();
        }
    };

    /**
     * The resolved {@link Tracer} to delegate the global tracing implementation to.<br>
     * This can be either an {@link #register(Tracer) explicitly registered delegate}
     * or the automatically resolved Tracer implementation.<br>
     * Management of this reference is the responsibility of the {@link #tracer()} method.
     */
    private static final AtomicReference<Tracer> DELEGATE = new AtomicReference<Tracer>();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private GlobalTracer() {
        throw new UnsupportedOperationException();
    }

    /**
     * Explicit registration of a configured {@link Tracer} to back the behaviour
     * of the {@link #tracer() global tracer}.
     * <p>
     * The previous global tracer is returned so it can be restored if necessary.
     *
     * @param delegate Tracer to delegate the tracing implementation to.
     * @return The previous global tracer.
     */
    public static Tracer register(final Tracer delegate) {
        final Tracer previous = DELEGATE.getAndSet(ActiveSpanTracer.wrap(delegate));
        LOGGER.log(Level.INFO, delegate == null ? "Cleared GlobalTracer delegate registration."
                : "Registered GlobalTracer delegate: {0}.", delegate);
        return previous;
    }

    /**
     * This method returns the {@link #register(Tracer) explicitly registered} Tracer implementation,
     * or attempts to lazily load the available implementation according to the standard Java
     * {@link java.util.ServiceLoader ServiceLoader}.
     * <p>
     * If no delegate is found, the {@link io.opentracing.NoopTracer NoopTracer} will be returned.
     * In this case no {@link ActiveSpanManager#activeSpan() globally-active spans} will be created.
     *
     * @return The non-<code>null</code> global tracer to use.
     */
    public static Tracer tracer() {
        Tracer instance = DELEGATE.get();
        if (instance == null) {
            final Tracer singleton = ActiveSpanTracer.wrap(SingletonServiceLoader.loadSingleton(Tracer.class, DEFAULT_PROVIDER));
            while (instance == null && singleton != null) {
                DELEGATE.compareAndSet(null, singleton);
                instance = DELEGATE.get();
            }
            LOGGER.log(Level.INFO, "Using global Tracer implementation: {0}.", instance);
        }
        LOGGER.log(Level.FINEST, "Global tracer: {0}.", instance);
        return instance;
    }

}
