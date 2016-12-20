package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.global.GlobalTracer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenience {@link Callable} wrapper that will execute within a new {@link Span} if an
 * {@link #withOperationName(String) operationName} is provided.<br>
 * If no operationName is provided, the call will be executed without starting a new Span.
 *
 * @author Sjoerd Talsma
 */
public class TracedCallable<T> implements Callable<T> {
    private static final Logger LOGGER = Logger.getLogger(TracedCallable.class.getName());

    protected final Callable<T> delegate;
    protected String operationName = "";
    protected SpanContext parentContext = null;

    protected TracedCallable(Callable<T> delegate) {
        if (delegate == null) throw new NullPointerException("Callable delegate is <null>.");
        this.delegate = delegate;
    }

    public static <T> TracedCallable<T> of(Callable<T> delegate) {
        return new TracedCallable<T>(delegate);
    }

    public TracedCallable<T> withOperationName(String operationName) {
        this.operationName = operationName;
        return this;
    }

    public TracedCallable<T> asChildOf(SpanContext parent) {
        this.parentContext = parent;
        return this;
    }

    public T call() throws Exception {
        Span newSpan = null;
        Exception callException = null, closeException = null;
        try {

            SpanBuilder spanBuilder = GlobalTracer.tracer().buildSpan(operationName);
            if (spanBuilder == null) {
                LOGGER.log(Level.WARNING, "Tracer returned SpanBuilder <null> for operation \"{0}\"!", operationName);
            } else {
                if (parentContext != null) {
                    spanBuilder = spanBuilder.asChildOf(parentContext);
                }
                newSpan = spanBuilder.start();
            }
            return delegate.call();

        } catch (Exception callEx) {
            callException = callEx;
        } finally {
            if (newSpan != null) try {
                newSpan.close();
            } catch (Exception closeEx) {
                closeException = closeEx;
            }
        }
        throw ThrowableSupport.addSuppressed(callException, closeException);
    }

    /**
     * Utility to call Throwable.addSuppressed() method form Java 6 code if it is running in a Java 7 JVM.
     * If the JVM is 1.6, the 'toBeSuppressed' exception will be logged instead.
     */
    private static final class ThrowableSupport {
        private static final Method JAVA7_ADDSUPPRESSED = reflectAddSuppressedMethod();

        /**
         * @param mainException  The main exception to be returned if non-null.
         * @param toBeSuppressed The 'alternate exception' to be added to the main exception.
         * @return mainException if non-null, otherwise toBeSuppressed.
         */
        private static Exception addSuppressed(Exception mainException, Exception toBeSuppressed) {
            if (mainException == null) return toBeSuppressed;
            else if (toBeSuppressed == null) return mainException;
            else if (JAVA7_ADDSUPPRESSED == null) { // Java 1.6
                LOGGER.log(Level.WARNING, "Exception closing new span.", toBeSuppressed);
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

        private static Method reflectAddSuppressedMethod() {
            try {
                return Throwable.class.getMethod("addSuppressed", Throwable.class);
            } catch (NoSuchMethodException addSuppressedNotFound) {
                LOGGER.log(Level.FINEST, "Older JVM encountered where addSuppressed is not available.");
                return null;
            }
        }
    }

}
