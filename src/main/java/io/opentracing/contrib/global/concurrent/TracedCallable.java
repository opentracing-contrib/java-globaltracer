package io.opentracing.contrib.global.concurrent;

import com.google.common.io.Closer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.global.GlobalTracer;

import java.util.concurrent.Callable;

/**
 * Wrapper for {@link Callable} to execute within a new {@link Span} from the {@link GlobalTracer}.
 * The {@link SpanBuilder#start() span will start} when the {@link #call() call is started}
 * and will be {@link Span#close() closed} when it is done.
 * <p>
 * This results in code for tracing a background thread to be similar to a normal trace:
 * <p>
 * <em>Current thread example:</em><br>
 * <pre><code>
 *     try (Span span = GlobalTracer.get().buildSpan("myOperation").start()) {
 *         // ... traced block of code ...
 *     }
 * </code></pre>
 * <p>
 * <em>Background thread example:</em><br>
 * <pre><code>
 *     Callable&lt;?&gt; call = ...traced block of code...
 *     Future&lt;?&gt; future = executor.submit(TracedCallable.of("myOperation", call));
 * </code></pre>
 * <p>
 * Or, with some 'known' parent span:<br>
 * <pre><code>
 *     Span parent = ...;
 *     try (Span span = GlobalTracer.get().buildSpan("myOperation").asChildOf(parent.context()).start()) {
 *         // ... traced block of code ...
 *     }
 * vs.
 *     Callable&lt;?&gt; call = ...traced block of code...
 *     Future&lt;?&gt; future = executor.submit(TracedCallable.of("myOperation", call).asChildOf(parent.context());
 * </code></pre>
 */
public final class TracedCallable<T> implements Callable<T> {

    private final Callable<T> delegate;
    private String operationName = "";
    private SpanContext parentContext = null;

    TracedCallable(String operationName, Callable<T> delegate) {
        if (delegate == null) throw new NullPointerException("Callable delegate is <null>.");
        this.delegate = delegate;
        this.operationName = operationName;
    }

    /**
     * Wraps the <code>delegate</code> call to execute in a new {@link Span} from the {@link GlobalTracer}
     * that starts when the delegate call starts and finishes when it is done.
     * <p>
     * Tracing code in a background thread becomes very similar to tracing code in the current thread:
     * <p>
     * <em>Current thread example:</em><br>
     * <pre><code>
     *     try (Span span = GlobalTracer.get().buildSpan("myOperation").start()) {
     *         // ... traced block of code ...
     *     }
     * </code></pre>
     * <p>
     * <em>Background thread example:</em><br>
     * <pre><code>
     *     Callable&lt;?&gt; call = ...traced block of code...
     *     Future&lt;?&gt; future = executor.submit(TracedCallable.of("myOperation", call));
     * </code></pre>
     *
     * @param operationName The <code>operationName</code> of the new span around the delegate.
     * @param delegate      The delegate call being traced.
     * @param <T>           The return type of the call.
     * @return The traced call.
     * @see #asChildOf(SpanContext)
     */
    public static <T> TracedCallable<T> of(String operationName, Callable<T> delegate) {
        return new TracedCallable<T>(operationName, delegate);
    }

    /**
     * Sets the parent context for this call.
     *
     * @param parent The parent context for the call.
     * @return This TracedCallable for method chaining.
     */
    public TracedCallable<T> asChildOf(SpanContext parent) {
        this.parentContext = parent;
        return this;
    }

    /**
     * Obtains a new {@link SpanBuilder} from the {@link GlobalTracer} and configures it.
     *
     * @return The spanbuilder for this TracedCallable.
     * @see #asChildOf(SpanContext)
     */
    private SpanBuilder spanBuilder() {
        SpanBuilder spanBuilder = GlobalTracer.get().buildSpan(operationName);
        if (parentContext != null) spanBuilder = spanBuilder.asChildOf(parentContext);
        return spanBuilder;
    }

    /**
     * This method {@linkplain #spanBuilder() builds a new span} and starts it before calling the delegate.
     * After the call is finished, the started span is {@linkplain Span#close() closed} again.
     *
     * @return The result from the delegate call.
     * @throws Exception in case the delegate call threw an exception
     *                   or there were exceptions starting or closing a new span.
     */
    public T call() throws Exception {
        Closer closer = Closer.create(); // To simulate try-with-resources on Java 6
        try {

            closer.register(spanBuilder().start());
            return delegate.call();

        } catch (Exception callEx) {
            throw closer.rethrow(callEx);
        } finally {
            closer.close();
        }
    }
}
