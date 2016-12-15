package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.contrib.activespan.ActiveSpanManager;
import io.opentracing.contrib.global.GlobalTracer;

import java.util.concurrent.Callable;

import static io.opentracing.contrib.global.concurrent.AddSuppressedSupport.addSuppressedOrLog;

/**
 * Convenience {@link Callable} wrapper that will execute with the {@link ActiveSpanManager#activeSpan() active span}
 * of the scheduling process.
 * <p>
 * Furthermore, a new {@link Span} will be started <em>as child of this active span</em>
 * around the call if a non-<code>null</code> {@link #withOperationName(String) operationName} is provided.
 *
 * @author Sjoerd Talsma
 */
public class TracedCallable<V> extends SpanAwareCallable<V> {

    protected TracedCallable(Callable<V> delegate, Span activeSpanOfScheduler) {
        super(new NewSpanCallable<V>(delegate), activeSpanOfScheduler);
    }

    public static <T> TracedCallable<T> of(Callable<T> delegate) {
        return new TracedCallable<T>(delegate, ActiveSpanManager.activeSpan());
    }

    public TracedCallable<V> withOperationName(String operationName) {
        ((NewSpanCallable<?>) delegate).operationName = operationName;
        return this;
    }

    private static class NewSpanCallable<V> implements Callable<V> {
        private final Callable<V> delegate;

        private String operationName;

        private NewSpanCallable(Callable<V> delegate) {
            if (delegate == null) throw new NullPointerException("Callable delegate is <null>.");
            this.delegate = delegate;
        }

        public V call() throws Exception {
            Span newSpan = null;
            Exception callException = null, closeException = null;
            try {

                if (operationName != null) {
                    newSpan = GlobalTracer.tracer()
                            .buildSpan(operationName)
                            .asChildOf(ActiveSpanManager.activeSpan())
                            .start();
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

}
