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
 * Forwards all methods to another tracer that can be configured in one of two ways:
 * <ol>
 * <li>Explicitly, calling {@link #set(Tracer)} with a configured tracer, or:</li>
 * <li>Automatically using the Java {@link ServiceLoader} SPI mechanism to load a {@link Tracer} from the classpath.</li>
 * </ol>
 * <p>
 * When the tracer is needed it is lazily looked up using the following rules:
 * <ol type="a">
 * <li>The last-{@link #set(Tracer) set} tracer always takes precedence.</li>
 * <li>If no tracer was set, one is looked up from the {@link ServiceLoader}.<br>
 * The {@linkplain GlobalTracer} will not attempt to choose between implementations:</li>
 * <li>If no single implementation is found, the {@link io.opentracing.NoopTracer NoopTracer} will be used.</li>
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
        return instance;
    }

    /**
     * Returns the constant {@linkplain GlobalTracer}.
     * <p>
     * All methods are forwarded to the currently configured tracer.<br>
     * Until a tracer is {@link #set(Tracer) explicitly configured},
     * one is looked up from the {@link ServiceLoader},
     * falling back to the {@link io.opentracing.NoopTracer NoopTracer}.<br>
     * A tracer can be re-configured at any time.
     * For example, the tracer used to extract a span may be different than the one that injects it.
     *
     * @return The global tracer constant.
     * @see #set(Tracer)
     */
    public static Tracer get() {
        return INSTANCE;
    }

    /**
     * Explicitly configures a {@link Tracer} to back the behaviour of the {@link #get() global tracer}.
     * <p>
     * The previous global tracer is returned so it can be restored later if necessary.
     *
     * @param tracer Tracer to use as global tracer.
     * @return The previous global tracer or <code>null</code> if there was none.
     */
    public static Tracer set(final Tracer tracer) {
        if (tracer instanceof GlobalTracer) {
            LOGGER.log(Level.FINE, "Attempted to set the GlobalTracer as delegate of itself.");
            return INSTANCE.globalTracer.get(); // no-op, return 'previous' tracer.
        }
        Tracer previous = INSTANCE.globalTracer.getAndSet(tracer);
        LOGGER.log(Level.INFO, "Set GlobalTracer to {0} (previously {1}).", new Object[]{tracer, previous});
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

}
