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
 */
public class TracedCallable<T> implements Callable<T> {
    private static final Logger LOGGER = Logger.getLogger(TracedCallable.class.getName());

    private final Callable<T> delegate;
    private String operationName = "";
    private SpanContext parentContext = null;

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

    protected Span startNewSpan() {
        SpanBuilder spanBuilder = GlobalTracer.get().buildSpan(operationName);
        if (parentContext != null) spanBuilder = spanBuilder.asChildOf(parentContext);
        return spanBuilder.start();
    }

    /**
     * This method {@linkplain #startNewSpan() starts a new span} and calls the delegate, returning the result.
     * Finally, the started span is {@linkplain Span#close() closed} again.
     *
     * @return The result from the delegate call.
     * @throws Exception in case the delegate call threw an exception
     *                   or there were exceptions starting or closing a new span.
     */
    public T call() throws Exception {
        // The javadoc doesn't mention the details of the errorhandling
        // because they are in line with normal try-with-resources logic:
        // - try to close resources created (started span) whether the call succeeded or failed.
        // - in case closing fails: a) throw the call exception if there was one (but addSuppressed the close failure)
        //                       or b) if there was no call exception throw the close failure instead.

        Span newSpan = null;            // so it can be closed
        Exception callException = null; // so it can be thrown from finally if necessary
        try {

            newSpan = startNewSpan();
            return delegate.call();

        } catch (Exception callEx) {
            callException = callEx;
            throw callException;
        } finally {
            if (newSpan != null) try {
                newSpan.close();
            } catch (Exception closeFailure) {
                //noinspection ThrowFromFinallyBlock
                throw callException == null ? closeFailure
                        : ThrowableSupport.addSuppressed(callException, closeFailure); // Don't shadow callException!
            }
        }
    }

    /**
     * Utility to call Throwable.addSuppressed() method form Java 6 code if it is running in a Java 7 JVM.
     * If the JVM is 1.6, the 'toBeSuppressed' exception will be logged instead.
     */
    private static final class ThrowableSupport {
        private static final Method JAVA7_ADDSUPPRESSED = reflectAddSuppressedMethod();

        private static Exception addSuppressed(Exception mainException, Exception toBeSuppressed) {
            if (JAVA7_ADDSUPPRESSED == null) { // Running in old JVM
                LOGGER.log(Level.WARNING, "Exception closing the started span.", toBeSuppressed);
                return mainException;
            }

            try {
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
