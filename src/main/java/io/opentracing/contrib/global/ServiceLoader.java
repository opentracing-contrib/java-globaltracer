package io.opentracing.contrib.global;

import javax.imageio.spi.ServiceRegistry;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loader class to delegate to JDK 6 ServiceLoader or fallback to the old {@link ServiceRegistry} (only for java 1.5).
 *
 * @param <SVC> The type of service to load.
 */
final class ServiceLoader<SVC> implements Iterable<SVC> {
    private static final Logger LOGGER = Logger.getLogger(ServiceLoader.class.getName());

    private final Class<SVC> serviceType;
    private final Iterable<SVC> delegate;

    @SuppressWarnings("unchecked")
        // Type is actually safe, although we use reflection.
    ServiceLoader(Class<SVC> serviceType) {
        if (serviceType == null) throw new NullPointerException("Type of service to load is <null>.");
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

    /**
     * Loads the singleton service implementation or falls back to the default service instead.<br>
     * A warning is logged when there are more than one service implementations encountered
     * because choosing which implementation to use is up to the application, not this library.
     *
     * @param <SVC>           The service type to be loaded.
     * @param serviceType     The service type to be loaded.
     * @param defaultProvider The default service provider, providing a non-<code>null</code> default implemenation.
     * @return The singleton service instance.
     */
    static <SVC> SVC loadSingleton(final Class<SVC> serviceType, final Callable<SVC> defaultProvider) {
        SVC foundSingleton = null;
        for (final Iterator<SVC> implementations = new ServiceLoader<SVC>(serviceType).iterator();
             foundSingleton == null && implementations.hasNext(); ) {
            final SVC implementation = implementations.next();
            if (implementation != null) {
                LOGGER.log(Level.FINEST, "Service loaded: {0}.", implementation);
                if (implementations.hasNext()) { // Don't actually load the next implementation, fall-back to default.
                    LOGGER.log(Level.WARNING, "More than one {0} service implementation found. " +
                            "Falling back to default implementation.", serviceType.getSimpleName());
                    break;
                } else {
                    foundSingleton = implementation;
                }
            }
        }

        if (foundSingleton == null) try {
            foundSingleton = defaultProvider.call();
            if (foundSingleton == null)
                throw new NullPointerException("Default service provider returned <null> " + serviceType.getSimpleName() + "!");
        } catch (Exception fallbackException) {
            throw new IllegalStateException("Exception creating default singleton " + serviceType.getSimpleName()
                    + " service.", fallbackException);
        }
        return foundSingleton;
    }

}
