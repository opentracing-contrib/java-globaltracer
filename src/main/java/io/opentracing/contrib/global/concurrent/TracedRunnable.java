package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.SpanContext;

import java.util.concurrent.Callable;

/**
 * Convenience {@link Runnable} wrapper that will execute within a new {@link Span} if an
 * {@link #withOperationName(String) operationName} is provided.<br>
 * If no operationName is provided, the call will be executed without starting a new Span.
 */
public class TracedRunnable implements Runnable {

    protected TracedCallable<Void> tracedCall;

    protected TracedRunnable(final Runnable delegate) {
        if (delegate == null) throw new NullPointerException("Runnable delegate is <null>.");
        this.tracedCall = new TracedCallable<Void>(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                delegate.run();
                return null;
            }
        });
    }

    public static TracedRunnable of(Runnable delegate) {
        return new TracedRunnable(delegate);
    }

    public TracedRunnable withOperationName(String operationName) {
        this.tracedCall = tracedCall.withOperationName(operationName);
        return this;
    }

    public TracedRunnable asChildOf(SpanContext parent) {
        this.tracedCall = tracedCall.asChildOf(parent);
        return this;
    }

    public void run() {
        try {
            tracedCall.call();
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }

}
