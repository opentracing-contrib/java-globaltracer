package io.opentracing.contrib.global;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import nl.talsmasoftware.context.Context;
import nl.talsmasoftware.context.ContextManager;

import javax.imageio.spi.ServiceRegistry;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
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
 * <p>
 * Spans and their contexts created by this <code>GlobalTracer</code> will propagate along with standard
 * <code>ContextSnapshot</code> propagation.
 *
 * @author Sjoerd Talsma
 * @see Tracer
 */
public final class GlobalTracer {
    private static final Logger LOGGER = Logger.getLogger(GlobalTracer.class.getName());

    /**
     * Use the standard Java SPI <code>ServiceLoader</code> concept to look for a registered Tracer delegate if no
     * {@link #register(Tracer) explicit delegate} was provided (including a pre-java6 workaround).
     */
    private static final Loader<Tracer> DELEGATES = new Loader<Tracer>(Tracer.class);

    /**
     * The resolved {@link Tracer} to delegate the global tracing implementation to.<br>
     * This can be either an {@link #register(Tracer) explicitly registered delegate} or an
     * {@link #DELEGATES automatically resolved Tracer implementation}.<br>
     * Management of this reference is the responsibility of the {@link #tracer()} method.
     */
    private static final AtomicReference<Tracer> delegate = new AtomicReference<Tracer>();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private GlobalTracer() {
        throw new UnsupportedOperationException();
    }

    /**
     * This method allows explicit registration of a configured {@link Tracer} implementation to back the behaviour
     * of the {@link #activeSpan() active global span} objects.
     *
     * @param delegate The delegate tracer to delegate the global tracing implementation to.
     */
    public static void register(Tracer delegate) {
        GlobalTracer.delegate.set(delegate != null ? new GlobalSpanTracer(delegate) : null);
        LOGGER.log(Level.INFO, "Registered GlobalTracer delegate: {0}.", delegate);
    }

    /**
     * This method returns the {@link #register(Tracer) explicitly registered} Tracer implementation,
     * or attempts to lazily load an available implementation according to the standard Java SPI conventions.
     * <p>
     * If no delegate is found, the {@link io.opentracing.NoopTracer NoopTracer} will be returned and no
     * {@link #activeSpan() globally-active spans} will be created.
     *
     * @return The non-<code>null</code> global tracer to use.
     */
    public static Tracer tracer() {
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
     * Static method to return the currently active global {@link Span}.<br>
     * There is no guarantee that there is an active {@link Span} in all situations.
     *
     * @return The currently active global Span, or <code>empty</code> if there is no Span currently active.
     */
    public static Span activeSpan() {
        return GlobalSpan.activeContext();
    }

    /**
     * Manager class that is able to get and set the globally active {@link Span} through the use of the common
     * {@link Context} concept.<br>
     * This manager is declared as ContextManager provider for global {@link Span spans} in the
     * <code>"/META-INF/services/nl.talsmasoftware.context.ContextManager"</code> service file.
     *
     * @author Sjoerd Talsma
     */
    public static class Manager implements ContextManager<Span> {
        public Context<Span> initializeNewContext(Span value) {
            return new GlobalSpan(value, false);
        }

        public Context<Span> getActiveContext() {
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
            this.delegate = delegate;
            if (delegate == null) throw new NullPointerException("Delegate Tracer is <null>.");
        }

        public SpanBuilder buildSpan(String operationName) {
            return new GlobalSpanBuilder(delegate.buildSpan(operationName));
        }

        public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
            delegate.inject(spanContext, format, carrier);
        }

        public <C> SpanContext extract(Format<C> format, C carrier) {
            return delegate.extract(format, carrier);
        }

        public String toString() {
            return "GlobalTracer{delegate=" + delegate + '}';
        }
    }

    /**
     * Loader class to delegate to JDK 6 ServiceLoader or fallback to the old {@link ServiceRegistry}.
     *
     * @param <SVC> The type of service to load.
     */
    private static final class Loader<SVC> implements Iterable<SVC> {
        private final Class<SVC> serviceType;
        private final Iterable<SVC> delegate;

        @SuppressWarnings("unchecked") // Type is actually safe, although we use reflection.
        private Loader(Class<SVC> serviceType) {
            this.serviceType = serviceType;
            Iterable<SVC> serviceLoader = null;
            try { // Attempt to use Java 1.6 ServiceLoader:
                // ServiceLoader.load(ContextManager.class, ContextManagers.class.getClassLoader());
                serviceLoader = (Iterable<SVC>) Class.forName("java.util.ServiceLoader")
                        .getDeclaredMethod("load", Class.class, ClassLoader.class)
                        .invoke(null, serviceType, serviceType.getClassLoader());
            } catch (ClassNotFoundException cnfe) {
                LOGGER.log(Level.FINEST, "Java 6 ServiceLoader not found, falling back to the imageio ServiceRegistry.");
            } catch (NoSuchMethodException nsme) {
                LOGGER.log(Level.SEVERE, "Could not find the 'load' method in the JDK's ServiceLoader.", nsme);
            } catch (IllegalAccessException iae) {
                LOGGER.log(Level.SEVERE, "Not allowed to call the 'load' method in the JDK's ServiceLoader.", iae);
            } catch (InvocationTargetException ite) {
                throw new IllegalStateException(String.format(
                        "Exception calling the 'load' method in the JDK's ServiceLoader for the %s service.",
                        serviceType.getSimpleName()), ite.getCause());
            }
            this.delegate = serviceLoader;
        }

        public Iterator<SVC> iterator() {
            return delegate != null ? delegate.iterator()
                    : ServiceRegistry.lookupProviders(serviceType, serviceType.getClassLoader());
        }
    }
}
