package io.opentracing.contrib.global;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.global.concurrent.TracedCallable;
import io.opentracing.contrib.global.concurrent.TracedRunnable;
import io.opentracing.propagation.Format;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@linkplain GlobalTracer} forwards all methods to a single {@link Tracer} implementation that can be
 * instantiated in one of two ways:
 * <ol>
 * <li>Explicitly, by calling {@link #setTracer(Tracer)} with a configured tracer implementation, or:</li>
 * <li>Automatically by using the Java <code>ServiceLoader</code> SPI mechanism to load an implementation
 * from the classpath.</li>
 * </ol>
 *
 * @author Sjoerd Talsma
 */
public final class GlobalTracer implements Tracer {
    private static final Logger LOGGER = Logger.getLogger(GlobalTracer.class.getName());

    private static final GlobalTracer INSTANCE = new GlobalTracer();

    /**
     * The resolved {@link Tracer} to delegate the global tracing implementation to.<br>
     * This can be either an {@link #setTracer(Tracer) explicitly set delegate}
     * or the automatically resolved Tracer implementation.<br>
     * Management of this reference is the responsibility of the {@link #lazyTracer()} method.
     */
    private final AtomicReference<Tracer> globalTracer = new AtomicReference<Tracer>();

    private GlobalTracer() {
    }

    private Tracer lazyTracer() {
        Tracer instance = globalTracer.get();
        if (instance == null) {
            final Tracer singleton = loadSingleton();
            while (instance == null && singleton != null) { // for race condition only, should rarely happen.
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
     * <li>If an explicitly configured tracer was provided via the {@link #setTracer(Tracer)} method,
     * that will always take precedence over automatically provided tracer instances.</li>
     * <li>A Tracer implementation can be automatically provided using the Java {@link ServiceLoader} through the
     * <code>META-INF/services/io.opentracing.Tracer</code> service definition file.<br>
     * The {@linkplain GlobalTracer} class will not attempt to choose between implementations;
     * if more than one is found by the {@link ServiceLoader service loader},
     * a warning is logged and tracing is disabled by falling back to the default implementation:</li>
     * <li>If no tracer implementation is available, the {@link io.opentracing.NoopTracer NoopTracer}
     * will be used.</li>
     * </ol>
     *
     * @return The global tracer.
     */
    public static Tracer tracer() {
        return INSTANCE;
    }

    /**
     * Explicit registration of a configured {@link Tracer} to back the behaviour
     * of the {@link #tracer() global tracer}.
     * <p>
     * The previous global tracer is returned so it can be restored later if necessary.
     *
     * @param tracer Tracer to use as global tracer.
     * @return The previous global tracer.
     */
    public static Tracer setTracer(final Tracer tracer) {
        if (tracer instanceof GlobalTracer) {
            throw new IllegalArgumentException("Attempted to set the GlobalTracer as delegate of itself.");
        }
        Tracer previous = INSTANCE.globalTracer.getAndSet(tracer);
        logChangedTracer(tracer, previous);
        return previous;
    }

    /**
     * Wraps the {@link Callable} to execute within a new {@link Span} if an
     * {@link TracedCallable#withOperationName(String) operationName} is also specified.<br>
     * If no operationName is provided, the callable will execute as-is without starting a new span.
     *
     * @param operationName Name of the traced operation.
     * @param callable      Callable to wrap.
     * @param <V>           Return type of the wrapped call.
     * @return The wrapped call.
     * @see TracedCallable#withOperationName(String)
     */
    public static <V> TracedCallable<V> traced(String operationName, Callable<V> callable) {
        return TracedCallable.of(callable).withOperationName(operationName);
    }

    /**
     * Wraps the {@link Runnable} to execute within a new {@link Span} if an
     * {@link TracedCallable#withOperationName(String) operationName} is also specified.<br>
     * If no operationName is provided, the callable will execute as-is without starting a new span.
     *
     * @param operationName Name of the traced operation.
     * @param runnable      Runnable to wrap.
     * @return The wrapped call.
     * @see TracedRunnable#withOperationName(String)
     */
    public static TracedRunnable traced(String operationName, Runnable runnable) {
        return TracedRunnable.of(runnable);
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
    private static Tracer loadSingleton() {
        Tracer foundSingleton = null;
        for (Iterator<Tracer> implementations =
             ServiceLoader.load(Tracer.class, Tracer.class.getClassLoader()).iterator();
             foundSingleton == null && implementations.hasNext(); ) {
            final Tracer implementation = implementations.next();
            if (implementation != null && !(implementation instanceof GlobalTracer)) {
                LOGGER.log(Level.FINEST, "Tracer service loaded: {0}.", implementation);
                if (implementations.hasNext()) { // Don't actually load the next implementation, fall-back to default.
                    LOGGER.log(Level.WARNING, "More than one Tracer service implementation found. " +
                            "Falling back to NoopTracer implementation.");
                    foundSingleton = NoopTracerFactory.create();
                } else {
                    foundSingleton = implementation;
                }
            }
        }
        if (foundSingleton == null) {
            LOGGER.log(Level.FINEST, "No Tracer service implementation found. Falling back to NoopTracer implementation.");
            foundSingleton = NoopTracerFactory.create();
        }
        return foundSingleton;
    }

    private static void logChangedTracer(Tracer newTracer, Tracer oldTracer) {
        Level loglevel = Level.INFO;
        String message = "Replaced GlobalTracer {1} with {0}.";
        if (newTracer == null) {
            message = "Cleared GlobalTracer registration.";
            if (oldTracer == null) loglevel = Level.FINEST;
        } else if (oldTracer == null) {
            message = "Set GlobalTracer: {0}.";
        }
        LOGGER.log(loglevel, message, new Object[]{newTracer, oldTracer});
    }

}
