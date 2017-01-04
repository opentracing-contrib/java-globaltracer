package io.opentracing.contrib.global;

import io.opentracing.NoopTracerFactory;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@linkplain GlobalTracer} forwards all methods to a single {@link Tracer} implementation that can be
 * instantiated in one of two ways:
 * <ol>
 * <li>Explicitly, by calling {@link #set(Tracer)} with a configured tracer implementation, or:</li>
 * <li>Automatically by using the Java <code>ServiceLoader</code> SPI mechanism to load an implementation
 * from the classpath.</li>
 * </ol>
 */
public final class GlobalTracer implements Tracer {
    private static final Logger LOGGER = Logger.getLogger(GlobalTracer.class.getName());

    /**
     * Singleton instance.
     * <p>
     * Since we cannot prevent people using {@linkplain #get() GlobalTracer.get()} as a constant,
     * this guarantees that references obtained before, during or after initialization
     * all behave as if obtained <em>after</em> initialization once properly initialized.<br>
     * As a minor additional benefit it makes it harder to circumvent the {@link Tracer} API.
     */
    private static final GlobalTracer INSTANCE = new GlobalTracer();

    /**
     * The resolved {@link Tracer} to delegate the global tracing implementation to.<br>
     * This can be either an {@link #set(Tracer) explicitly set delegate}
     * or the automatically resolved Tracer implementation.
     */
    private final AtomicReference<Tracer> globalTracer = new AtomicReference<Tracer>();

    private GlobalTracer() {
    }

    private Tracer lazyTracer() {
        Tracer instance = globalTracer.get();
        if (instance == null) {
            final Tracer singleton = loadSingleSpiImplementation();
            while (instance == null && singleton != null) { // handle rare race condition
                globalTracer.compareAndSet(null, singleton);
                instance = globalTracer.get();
            }
            LOGGER.log(Level.INFO, "Using GlobalTracer implementation: {0}.", instance);
        }
        LOGGER.log(Level.FINEST, "GlobalTracer: {0}.", instance);
        return instance;
    }

    /**
     * Returns the {@linkplain GlobalTracer} instance.
     * Upon first use of any tracing method, this tracer lazily determines which actual {@link Tracer}
     * implementation to use:
     * <ol type="a">
     * <li>If an explicitly configured tracer was provided via the {@link #set(Tracer)} method,
     * that will always take precedence over automatically resolved tracer instances.</li>
     * <li>A Tracer implementation can be automatically provided using the Java {@link ServiceLoader} through the
     * <code>META-INF/services/io.opentracing.Tracer</code> service definition file.<br>
     * The {@linkplain GlobalTracer} will not attempt to choose between implementations;
     * if more than one is found by the {@linkplain ServiceLoader service loader},
     * a warning is logged and tracing is disabled by falling back to the default implementation:</li>
     * <li>If no tracer implementation is available, the {@link io.opentracing.NoopTracer NoopTracer}
     * will be used.</li>
     * </ol>
     *
     * @return The global tracer.
     */
    public static Tracer get() {
        return INSTANCE;
    }

    /**
     * Explicit registration of a configured {@link Tracer} to back the behaviour
     * of the {@link #get() global tracer}.
     * <p>
     * The previous global tracer is returned so it can be restored later if necessary.
     *
     * @param tracer Tracer to use as global tracer.
     * @return The previous global tracer.
     */
    public static Tracer set(final Tracer tracer) {
        if (tracer instanceof GlobalTracer) {
            LOGGER.log(Level.FINE, "Attempted to set the GlobalTracer as delegate of itself.");
            return INSTANCE.globalTracer.get(); // no-op, return 'previous' tracer.
        }
        Tracer previous = INSTANCE.globalTracer.getAndSet(tracer);
        logChangedTracer(tracer, previous);
        return previous;
    }

    @Override
    public SpanBuilder buildSpan(String operationName) {
        return lazyTracer().buildSpan(operationName);
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        lazyTracer().inject(spanContext, format, carrier);
    }

    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        return lazyTracer().extract(format, carrier);
    }

    /**
     * Loads a single service implementation from {@link ServiceLoader}.
     *
     * @return The single service or a NoopTracer.
     */
    private static Tracer loadSingleSpiImplementation() {
        // Use the ServiceLoader to find the declared Tracer implementation.
        Iterator<Tracer> spiImplementations =
                ServiceLoader.load(Tracer.class, Tracer.class.getClassLoader()).iterator();
        if (spiImplementations.hasNext()) {
            Tracer foundImplementation = spiImplementations.next();
            if (!spiImplementations.hasNext()) {
                return foundImplementation;
            }
            LOGGER.log(Level.WARNING, "More than one Tracer service implementation found. " +
                    "Falling back to NoopTracer implementation.");
        }
        return NoopTracerFactory.create();
    }

    private static void logChangedTracer(Tracer newTracer, Tracer oldTracer) {
        Level loglevel = Level.INFO;
        String message = "Replaced GlobalTracer {1} with {0}.";
        if (newTracer == null) {
            message = "Cleared GlobalTracer registration.";
            if (oldTracer == null) loglevel = Level.FINEST;
        } else if (oldTracer == null) {
            message = "Set GlobalTracer: {0}.";
        } else if (newTracer.equals(oldTracer)) {
            loglevel = Level.FINEST;
        }
        LOGGER.log(loglevel, message, new Object[]{newTracer, oldTracer});
    }

}
