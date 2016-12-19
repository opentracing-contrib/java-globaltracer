package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.SpanContext;

import java.util.concurrent.Callable;

import static io.opentracing.contrib.global.concurrent.ActiveSpanManagerSupport.getActiveSpan;
import static io.opentracing.contrib.global.concurrent.ActiveSpanManagerSupport.isActiveSpanManagerSupported;

/**
 * Convenience {@link Runnable} wrapper that will execute within a new {@link Span} if an
 * {@link #withOperationName(String) operationName} is provided.<br>
 * If no operationName is provided, the call will be executed without starting a new Span.
 * <p>
 * If an <em>active span manager</em> is detected, the new span will become the <em>child</em> of the currently
 * active span from the caller.
 *
 * @author Sjoerd Talsma
 */
public class TracedRunnable implements Runnable {

    protected TracedCallable<Void> tracedCall;

    protected TracedRunnable(final Runnable delegate, SpanContext schedulerSpanContext) {
        this.tracedCall = new TracedCallable<Void>(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                delegate.run();
                return null;
            }
        }, schedulerSpanContext);
    }

    public static TracedRunnable of(Runnable delegate) {
        SpanContext active = isActiveSpanManagerSupported() ? getActiveSpan().context() : null;
        return new TracedRunnable(delegate, active);
    }

    public TracedRunnable withOperationName(String operationName) {
        this.tracedCall = tracedCall.withOperationName(operationName);
        return this;
    }

    public void run() {
        try {
            tracedCall.call();
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new IllegalStateException("Checked exception from delegate: " + ex.getMessage(), ex);
        }
    }

}
