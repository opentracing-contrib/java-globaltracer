package io.opentracing.contrib.global;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import nl.talsmasoftware.context.Context;
import nl.talsmasoftware.context.ContextManager;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * The <code>GlobalTracer</code> class is not a {@link Tracer} implementation of itself, but instead a utility-class
 * to centrally reference the {@link #getInstance() singleton instance} of the configured {@link Tracer} implementation.
 * <p>
 * The singleton instance can be instantiated in one of two ways:
 * <ol>
 * <li>Explicitly, by calling {@link #registerDelegate(Tracer)} with a configured tracer implementation, or:</li>
 * <li>Automatically by using the Java SPI mechanism to {@link ServiceLoader load an implementation}
 * from the classpath.</li>
 * </ol>
 * <p>
 * Spans and their contexts created by this <code>GlobalTracer</code> will propagate along with standard
 * <code>ContextSnapshot</code> propagation.
 *
 * @author Sjoerd Talsma
 * @see Tracer
 * @see ServiceLoader
 */
public class GlobalTracer {
    private static final Logger LOGGER = Logger.getLogger(GlobalTracer.class.getName());

    /**
     * Use the standard Java SPI {@link ServiceLoader} concept to look for a registered Tracer delegate if no
     * {@link #registerDelegate(Tracer) explicit delegate} was provided.
     */
    private static final ServiceLoader<Tracer> DELEGATES = ServiceLoader.load(Tracer.class);

    /**
     * The resolved {@link Tracer} to delegate the global tracing implementation to.<br>
     * This can be either an {@link #registerDelegate(Tracer) explicitly registered delegate} or an
     * {@link #DELEGATES automatically resolved Tracer implementation}.<br>
     * Management of this reference is the responsibility of the {@link #getInstance()} method.
     */
    private static final AtomicReference<Tracer> delegate = new AtomicReference<>();

    /**
     * This method returns the {@link #registerDelegate(Tracer) explicitly registered} Tracer implementation,
     * or attempts to {@link ServiceLoader load an available implementation} according to the standard
     * Java SPI conventions.
     * <p>
     * If no delegate is found, the {@link io.opentracing.NoopTracer NoopTracer} will be returned and no
     * {@link #activeSpan() globally-active spans} will be created.
     *
     * @return The global tracer to use.
     */
    public static Tracer getInstance() {
        Tracer instance = delegate.get();
        if (instance == null) {
            for (Tracer tracer : DELEGATES) {
                if (tracer != null) {
                    LOGGER.log(Level.FINE, "Tracer service loaded: {0}.", tracer);
                    while (instance == null) { // Retry for race conditions, shouldn't normally happen.
                        delegate.compareAndSet(null, new GlobalSpanTracer(tracer));
                        instance = delegate.get();
                    }
                    break;
                }
            }
        }
        if (instance == null) {
            instance = NoopTracerFactory.create(); // The 'no-op' tracer is not wrapped and won't register global spans.
            LOGGER.log(Level.FINE, "No global tracer instance was found; " +
                    "falling back to the \"no-op\" tracer implementation: {0}.", instance);
        }
        LOGGER.log(Level.FINEST, "Using tracer: {0}.", instance);
        return instance;
    }

    /**
     * This method allows explicit registration of a configured {@link Tracer} implementation to back the behaviour
     * of the {@link #activeSpan() active global span} objects.
     *
     * @param delegate The delegate tracer to delegate the global tracing implementation to.
     */
    public static void registerDelegate(Tracer delegate) {
        GlobalTracer.delegate.set(delegate != null ? new GlobalSpanTracer(delegate) : null);
        LOGGER.log(Level.INFO, "Registered GlobalTracer delegate: {0}.", delegate);
    }

    /**
     * Static method to return the currently active global {@link Span}.<br>
     * There is no guarantee that there is an active {@link Span} in all situations.
     *
     * @return The currently active global Span, or <code>empty</code> if there is no Span currently active.
     */
    public static Optional<Span> activeSpan() {
        return GlobalSpan.activeContext().flatMap(Context::getValue);
    }

    /**
     * Manager class that is able to get and set the globally active {@link Span} through the use of the common
     * {@link Context} concept.<br>
     * This manager is declared as ContextManager provider for global {@link Span spans} in the
     * <code>"/META-INF/services/nl.talsmasoftware.concurrency.context.ContextManager.properties"</code> service file.
     *
     * @author Sjoerd Talsma
     */
    public static class Manager implements ContextManager<Span> {
        @Override
        public Context<Span> initializeNewContext(Span value) {
            return new GlobalSpan(value);
        }

        @Override
        public Optional<Context<Span>> getActiveContext() {
            return GlobalSpan.activeContext();
        }
    }

    /**
     * Private wrapper class that delegates all tracing to the specified implementation but makes sure to register
     * all started {@link Span} instances as new {@link GlobalSpan} contexts.
     */
    private static class GlobalSpanTracer implements Tracer {
        private final Tracer delegate;

        private GlobalSpanTracer(Tracer delegate) {
            this.delegate = requireNonNull(delegate, "Delegate Tracer is <null>.");
        }

        @Override
        public SpanBuilder buildSpan(String operationName) {
            return new GlobalSpanBuilder(delegate.buildSpan(operationName));
        }

        @Override
        public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
            delegate.inject(spanContext, format, carrier);
        }

        @Override
        public <C> SpanContext extract(Format<C> format, C carrier) {
            return delegate.extract(format, carrier);
        }

        @Override
        public String toString() {
            return "GlobalTracer{delegate=" + delegate + '}';
        }
    }

}
