package io.opentracing.contrib.global.concurrent;

import io.opentracing.SpanContext;

import java.util.concurrent.Callable;

/**
 * TODO: Document TracedRunnable similar to {@link TracedCallable}
 */
public final class TracedRunnable implements Runnable {

    private TracedCallable<Void> tracedCall;

    private TracedRunnable(String operationName, final Runnable delegate) {
        if (delegate == null) throw new NullPointerException("Runnable delegate is <null>.");
        this.tracedCall = new TracedCallable<Void>(operationName, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                delegate.run();
                return null;
            }
        });
    }

    public static TracedRunnable of(String operationName, Runnable delegate) {
        return new TracedRunnable(operationName, delegate);
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
