package io.opentracing.contrib.global.concurrent;

import com.google.common.io.Closer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.global.GlobalTracer;

import java.util.concurrent.Callable;

/**
 * Convenience {@link Callable} wrapper that will execute within a new {@link Span} if an
 * {@link #withOperationName(String) operationName} is provided.<br>
 * If no operationName is provided, the call will be executed without starting a new Span.
 */
public class TracedCallable<T> implements Callable<T> {

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
        Closer closer = Closer.create(); // To simulate try-with-resources on Java 6
        try {

            closer.register(startNewSpan());
            return delegate.call();

        } catch (Exception callEx) {
            throw closer.rethrow(callEx);
        } finally {
            closer.close();
        }
    }
}
