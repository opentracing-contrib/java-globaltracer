package io.opentracing.contrib.global;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilty to load a singleton service instance from JDK {@link ServiceLoader}. A default provider can be specified
 */
final class SingletonServiceLoader {
    private static final Logger LOGGER = Logger.getLogger(SingletonServiceLoader.class.getName());

    private SingletonServiceLoader() {
        throw new UnsupportedOperationException();
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
     * @see ServiceLoader#load(Class, ClassLoader)
     */
    static <SVC> SVC loadSingleton(final Class<SVC> serviceType, final Callable<SVC> defaultProvider) {
        SVC foundSingleton = null;
        for (Iterator<SVC> implementations = ServiceLoader.load(serviceType, serviceType.getClassLoader()).iterator();
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
            if (foundSingleton == null) throw new NullPointerException("Default service provider returned <null> " +
                    serviceType.getSimpleName() + "!");
        } catch (Exception fallbackException) {
            throw new IllegalStateException("Exception creating default singleton " + serviceType.getSimpleName()
                    + " service.", fallbackException);
        }
        return foundSingleton;
    }

}
