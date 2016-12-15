package io.opentracing.contrib.global;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.activespan.ActiveSpanManager;
import io.opentracing.contrib.global.concurrent.SpanAwareCallable;
import io.opentracing.contrib.global.concurrent.SpanAwareRunnable;
import io.opentracing.contrib.global.concurrent.TracedCallable;
import io.opentracing.contrib.global.concurrent.TracedRunnable;

import java.util.Iterator;
import java.util.ServiceLoader;
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
 * @navassoc - uses - io.opentracing.contrib.activespan.ActiveSpanManager
 * @see Tracer
 */
public final class GlobalTracer {
    private static final Logger LOGGER = Logger.getLogger(GlobalTracer.class.getName());

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
     * Returns the {@link #register(Tracer) explicitly registered} Tracer implementation.
     * <p>
     * If no explicit registration exists, the Java {@link java.util.ServiceLoader ServiceLoader} is used to load
     * the {@link Tracer} service implementation.<br>
     * If zero or more than one service implementations are found,
     * the {@link io.opentracing.NoopTracer NoopTracer} will be returned.
     * <p>
     * Spans created from Tracers will automatically become the
     * {@link ActiveSpanManager#activeSpan() active span} when started and get
     * {@link ActiveSpanManager#deactivate(ActiveSpanManager.SpanDeactivator) deactivated} when finished or closed.
     *
     * @return The non-<code>null</code> global tracer to use.
     */
    public static Tracer tracer() {
        Tracer instance = DELEGATE.get();
        if (instance == null) {
            final Tracer singleton = ActiveSpanTracer.wrap(loadSingleton());
            while (instance == null && singleton != null) {
                DELEGATE.compareAndSet(null, singleton);
                instance = DELEGATE.get();
            }
            LOGGER.log(Level.INFO, "Using global Tracer implementation: {0}.", instance);
        }
        LOGGER.log(Level.FINEST, "Global tracer: {0}.", instance);
        return instance;
    }

    /**
     * Wraps the {@link Callable} to execute with the {@link ActiveSpanManager#activeSpan() active span}
     * from the scheduling thread.
     *
     * @param callable The callable to wrap.
     * @param <V>      The return type of the wrapped call.
     * @return The wrapped call executing with the active span of the scheduling process.
     * @see #traced(Callable)
     */
    public static <V> SpanAwareCallable<V> spanAware(Callable<V> callable) {
        return SpanAwareCallable.of(callable);
    }

    /**
     * Wraps the {@link Runnable} to execute with the {@link ActiveSpanManager#activeSpan() active span}
     * from the scheduling thread.
     *
     * @param runnable The runnable to wrap.
     * @return The wrapped runnable executing with the active span of the scheduling process.
     * @see #traced(Runnable)
     */
    public static SpanAwareRunnable spanAware(Runnable runnable) {
        return SpanAwareRunnable.of(runnable);
    }

    /**
     * Wraps the {@link Callable} to execute with the {@link ActiveSpanManager#activeSpan() active span}
     * from the scheduling thread.
     * <p>
     * Furthermore, a new {@link Span} will be started <em>as child of this active span</em>
     * around the call if a non-<code>null</code> {@link TracedCallable#withOperationName(String) operationName}
     * is provided.
     *
     * @param callable The callable to wrap.
     * @param <V>      The return type of the wrapped call.
     * @return The wrapped call.
     * @see #spanAware(Callable)
     * @see TracedCallable#withOperationName(String)
     */
    public static <V> TracedCallable<V> traced(Callable<V> callable) {
        return TracedCallable.of(callable);
    }

    /**
     * Wraps the {@link Runnable} to execute with the {@link ActiveSpanManager#activeSpan() active span}
     * from the scheduling thread.
     * <p>
     * Furthermore, a new {@link Span} will be started <em>as child of this active span</em>
     * around the call if a non-<code>null</code> {@link TracedCallable#withOperationName(String) operationName}
     * is provided.
     *
     * @param runnable The runnable to wrap.
     * @return The wrapped call.
     * @see #spanAware(Runnable)
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
                LOGGER.log(Level.FINEST, "Service loaded: {0}.", implementation);
                if (implementations.hasNext()) { // Don't actually load the next implementation, fall-back to default.
                    LOGGER.log(Level.WARNING, "More than one Tracer service implementation found. " +
                            "Falling back to default implementation.");
                    break;
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
