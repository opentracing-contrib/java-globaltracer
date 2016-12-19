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
 * The <code>GlobalTracer</code> forwards all methods to a single {@link Tracer} implementation that can be
 * instantiated in one of two ways:
 * <ol>
 * <li>Explicitly, by calling {@link #register(Tracer)} with a configured tracer implementation, or:</li>
 * <li>Automatically by using the Java <code>ServiceLoader</code> SPI mechanism to load an implementation
 * from the classpath.</li>
 * </ol>
 *
 * @author Sjoerd Talsma
 * @navassoc - provides 1 io.opentracing.Tracer
 * @navassoc - uses - io.opentracing.contrib.activespan.ActiveSpanManager
 * @see Tracer
 */
public final class GlobalTracer implements Tracer {
    private static final Logger LOGGER = Logger.getLogger(GlobalTracer.class.getName());

    private static final GlobalTracer INSTANCE = new GlobalTracer();

    /**
     * The resolved {@link Tracer} to delegate the global tracing implementation to.<br>
     * This can be either an {@link #register(Tracer) explicitly registered delegate}
     * or the automatically resolved Tracer implementation.<br>
     * Management of this reference is the responsibility of the {@link #tracer()} method.
     */
    private final AtomicReference<Tracer> globalTracer = new AtomicReference<Tracer>();

    /**
     * Private constructor to prevent outside instantiation of this singleton class.
     */
    private GlobalTracer() {
    }

    private Tracer getOrInitTracer() {
        Tracer instance = globalTracer.get();
        if (instance == null) {
            final Tracer singleton = loadSingleton();
            while (instance == null && singleton != null) {
                globalTracer.compareAndSet(null, singleton);
                instance = globalTracer.get();
            }
            LOGGER.log(Level.INFO, "Using GlobalTracer implementation: {0}.", instance);
        }
        LOGGER.log(Level.FINEST, "GlobalTracer: {0}.", instance);
        return instance;
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
        final Tracer previous = INSTANCE.globalTracer.getAndSet(delegate);
        if (delegate == null) {
            Level loglevel = previous == null ? Level.FINEST : Level.INFO;
            LOGGER.log(loglevel, "Cleared GlobalTracer registration.");
        } else {
            String message = previous == null ? "Registered GlobalTracer: {0}." : "Replaced GlobalTracer {1} with {0}.";
            LOGGER.log(Level.INFO, message, new Object[]{delegate, previous});
        }
        return previous;
    }

    /**
     * Returns the {@link #register(Tracer) explicitly registered} Tracer implementation.
     * <p>
     * If no explicit registration exists, the Java {@link java.util.ServiceLoader ServiceLoader} is used to load
     * the {@link Tracer} service implementation.<br>
     * If zero or more than one service implementations are found,
     * the {@link io.opentracing.NoopTracer NoopTracer} will be returned.
     *
     * @return The non-<code>null</code> global tracer to use.
     */
    public static Tracer tracer() {
        return INSTANCE.getOrInitTracer();
    }

    @Override
    public SpanBuilder buildSpan(String operationName) {
        return getOrInitTracer().buildSpan(operationName);
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        getOrInitTracer().inject(spanContext, format, carrier);
    }

    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        return getOrInitTracer().extract(format, carrier);
    }

    /**
     * Wraps the {@link Callable} to execute within a new {@link Span} if an
     * {@link TracedCallable#withOperationName(String) operationName} is also specified.<br>
     * If no operationName is provided, the callable will execute as-is without starting a new span.
     *
     * @param callable The callable to wrap.
     * @param <V>      The return type of the wrapped call.
     * @return The wrapped call.
     * @see TracedCallable#withOperationName(String)
     */
    public static <V> TracedCallable<V> traced(Callable<V> callable) {
        return TracedCallable.of(callable);
    }

    /**
     * Wraps the {@link Runnable} to execute within a new {@link Span} if an
     * {@link TracedCallable#withOperationName(String) operationName} is also specified.<br>
     * If no operationName is provided, the callable will execute as-is without starting a new span.
     *
     * @param runnable The runnable to wrap.
     * @return The wrapped call.
     * @see TracedRunnable#withOperationName(String)
     */
    public static TracedRunnable traced(Runnable runnable) {
        return TracedRunnable.of(runnable);
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
            if (implementation != null) {
                LOGGER.log(Level.FINEST, "Tracer service loaded: {0}.", implementation);
                if (implementations.hasNext()) { // Don't actually load the next implementation, fall-back to default.
                    LOGGER.log(Level.WARNING,
                            "More than one Tracer service implementation found. Falling back to default implementation.");
                    foundSingleton = NoopTracerFactory.create();
                } else {
                    foundSingleton = implementation;
                }
            }
        }
        if (foundSingleton == null) {
            LOGGER.log(Level.FINEST, "No Tracer service implementation found. Falling back to default implementation.");
            foundSingleton = NoopTracerFactory.create();
        }
        return foundSingleton;
    }

}
