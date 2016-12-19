package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to access <code>ActiveSpanManager</code> if detected on the classpath.
 *
 * @author Sjoerd Talsma
 */
final class ActiveSpanManagerSupport {
    private static final Logger LOGGER = Logger.getLogger(ActiveSpanManagerSupport.class.getName());

    /**
     * Whether or not the ActiveSpanManager class was found.
     */
    private static final boolean SUPPORTED;

    static {
        boolean activeSpanManagerFound;
        try {
            Class.forName("io.opentracing.contrib.activespan.ActiveSpanManager");
            activeSpanManagerFound = true;
        } catch (ClassNotFoundException cnfe) {
            LOGGER.log(Level.FINEST, "The ActiveSpanManager class was not found. Disabling its support.", cnfe);
            activeSpanManagerFound = false;
        }
        SUPPORTED = activeSpanManagerFound;
    }

    /**
     * @return Whether support for the active span manager was detected.
     */
    static boolean isActiveSpanManagerSupported() {
        return SUPPORTED;
    }

    /**
     * @return The active span from the manager or <code>null</code> if no support was detected.
     */
    static Span getActiveSpan() {
        Span activeSpan = null;
        if (SUPPORTED) try {
            activeSpan = io.opentracing.contrib.activespan.ActiveSpanManager.activeSpan();
        } catch (LinkageError notAvailable) {
            LOGGER.log(Level.WARNING, "The ActiveSpanManager is not available!", notAvailable);
        }
        return activeSpan;
    }

}
