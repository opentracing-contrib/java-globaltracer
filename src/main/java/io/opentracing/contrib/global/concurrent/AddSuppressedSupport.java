package io.opentracing.contrib.global.concurrent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unfortunately Java 1.6 does not yet support try-with-resources.
 * When running in a 1.7 JVM we really want to provide this behaviour.<br>
 * Fallback in java 6 is logging the suppressed exception to make sure to always return the 'main' exception.
 *
 * @author Sjoerd Talsma
 */
final class AddSuppressedSupport {
    private static final Logger LOGGER = Logger.getLogger(AddSuppressedSupport.class.getName());
    private static final Method JAVA7_ADDSUPPRESSED;

    static {
        Method addSuppressedMethod = null;
        try {
            addSuppressedMethod = Throwable.class.getMethod("addSuppressed", Throwable.class);
        } catch (NoSuchMethodException addSuppressedNotFound) {
            LOGGER.log(Level.FINEST, "Older JVM encountered where addSuppressed is not available.");
        }
        JAVA7_ADDSUPPRESSED = addSuppressedMethod;
    }

    /**
     * <ol>
     * <li>Returns <code>toBeSuppressed</code> if <code>mainException</code> is null.</li>
     * <li>For Java 1.6 VM logs stacktrace of <code>toBeSuppressed</code> and returns <code>mainException</code>.</li>
     * <li>Otherwise, calls <code>mainException.addSuppressed(toBeSuppressed)</code> and returns <code>mainException</code>.</li>
     * </ol>
     *
     * @param mainException  The main exception that occurred (if any).
     * @param toBeSuppressed The exception to be suppressed (required).
     * @param logmessage     Message to log suppressed stacktrace with if running in a Java 1.6 VM.
     * @return Nothing, throws either <code>mainException</code> or <code>toBeSuppressed</code>.
     */
    static Exception addSuppressedOrLog(Exception mainException, Exception toBeSuppressed, String logmessage) {
        if (mainException == null) return toBeSuppressed;
        else if (toBeSuppressed == null) return mainException;
        else if (JAVA7_ADDSUPPRESSED == null) { // Java 1.6
            LOGGER.log(Level.WARNING, logmessage, toBeSuppressed);
        } else try {
            JAVA7_ADDSUPPRESSED.invoke(mainException, toBeSuppressed);
        } catch (InvocationTargetException ite) {
            LOGGER.log(Level.WARNING, "Exception adding {1} as suppressed exception to {0}.",
                    new Object[]{mainException, toBeSuppressed, ite.getCause()});
        } catch (IllegalAccessException iae) {
            LOGGER.log(Level.WARNING, "Not allowed to add {1} as suppressed exception to {0}.",
                    new Object[]{mainException, toBeSuppressed, iae});
        }
        return mainException;
    }

}
