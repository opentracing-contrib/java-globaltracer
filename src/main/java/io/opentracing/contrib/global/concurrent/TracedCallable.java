package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.global.GlobalTracer;

import java.util.concurrent.Callable;

import static io.opentracing.contrib.global.concurrent.AddSuppressedSupport.addSuppressedOrLog;

/**
 * Convenience {@link Callable} wrapper that will execute within a new {@link Span} if an
 * {@link #withOperationName(String) operationName} is provided.<br>
 * If no operationName is provided, the call will be executed without starting a new Span.
 *
 * @author Sjoerd Talsma
 */
public class TracedCallable<T> implements Callable<T> {

    protected final Callable<T> delegate;
    protected String operationName = null;
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

            if (operationName != null) {
                SpanBuilder spanBuilder = GlobalTracer.tracer().buildSpan(operationName);
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
        throw addSuppressedOrLog(callException, closeException, "Exception closing new span.");
    }

}
