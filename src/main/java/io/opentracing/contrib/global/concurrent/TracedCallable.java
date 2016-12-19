package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.global.GlobalTracer;

import java.util.concurrent.Callable;

import static io.opentracing.contrib.global.concurrent.ActiveSpanManagerSupport.getActiveSpan;
import static io.opentracing.contrib.global.concurrent.ActiveSpanManagerSupport.isActiveSpanManagerSupported;
import static io.opentracing.contrib.global.concurrent.AddSuppressedSupport.addSuppressedOrLog;

/**
 * Convenience {@link Callable} wrapper that will execute within a new {@link Span} if an
 * {@link #withOperationName(String) operationName} is provided.<br>
 * If no operationName is provided, the call will be executed without starting a new Span.
 * <p>
 * If an <em>active span manager</em> is detected, the new span will become the <em>child</em> of the currently
 * active span from the caller.
 *
 * @author Sjoerd Talsma
 */
public class TracedCallable<T> implements Callable<T> {

    protected final Callable<T> delegate;

    /**
     * The active SpanContext from the scheduling thread.<br>
     * Note that absence of <code>ActiveSpanManager</code> <em>support</em> will result in a <code>null</code> context
     * and that a supported but absent <em>active span</em> will result in a <code>NoopSpanContext</code>.
     */
    protected final SpanContext schedulerSpanContext;

    protected String operationName;

    protected TracedCallable(Callable<T> delegate, SpanContext schedulerSpanContext) {
        if (delegate == null) throw new NullPointerException("Callable delegate is <null>.");
        this.delegate = delegate;
        this.schedulerSpanContext = schedulerSpanContext;
        this.operationName = null;
    }

    public static <T> TracedCallable<T> of(Callable<T> delegate) {
        SpanContext activeSpanContext = isActiveSpanManagerSupported() ? getActiveSpan().context() : null;
        return new TracedCallable<T>(delegate, activeSpanContext);
    }

    public TracedCallable<T> withOperationName(String operationName) {
        this.operationName = operationName;
        return this;
    }

    public T call() throws Exception {
        Span newSpan = null;
        Exception callException = null, closeException = null;
        try {

            if (operationName != null) {
                SpanBuilder spanBuilder = GlobalTracer.tracer().buildSpan(operationName);
                if (schedulerSpanContext != null) {
                    spanBuilder = spanBuilder.asChildOf(schedulerSpanContext);
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
