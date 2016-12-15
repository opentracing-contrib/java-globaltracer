package io.opentracing.contrib.global.concurrent;

import io.opentracing.Span;
import io.opentracing.contrib.global.ActiveSpanManager;
import io.opentracing.contrib.global.GlobalTracer;

import static io.opentracing.contrib.global.concurrent.AddSuppressedSupport.addSuppressedOrLog;

/**
 * Convenience {@link Runnable} wrapper that will execute with the {@link ActiveSpanManager#activeSpan() active span}
 * of the scheduling process.
 * <p>
 * Furthermore, a new {@link Span} will be started <em>as child of this active span</em>
 * around the call if a non-<code>null</code> {@link #withOperationName(String) operationName} is provided.
 *
 * @author Sjoerd Talsma
 */
public class TracedRunnable extends SpanAwareRunnable {

    protected TracedRunnable(Runnable delegate, Span activeSpanOfScheduler) {
        super(new NewSpanRunnable(delegate), activeSpanOfScheduler);
    }

    public static TracedRunnable of(Runnable delegate) {
        return new TracedRunnable(delegate, ActiveSpanManager.activeSpan());
    }

    public TracedRunnable withOperationName(String operationName) {
        ((NewSpanRunnable) delegate).operationName = operationName;
        return this;
    }

    private static class NewSpanRunnable implements Runnable {
        private final Runnable delegate;

        private String operationName;

        private NewSpanRunnable(Runnable delegate) {
            if (delegate == null) throw new NullPointerException("Callable delegate is <null>.");
            this.delegate = delegate;
        }

        public void run() {
            Span newSpan = null;
            RuntimeException runException = null, closeException = null;
            try {

                if (operationName != null) {
                    newSpan = GlobalTracer.tracer()
                            .buildSpan(operationName)
                            .asChildOf(ActiveSpanManager.activeSpan())
                            .start();
                }
                delegate.run();
                return;

            } catch (RuntimeException runEx) {
                runException = runEx;
            } finally {
                if (newSpan != null) try {
                    newSpan.close();
                } catch (RuntimeException closeEx) {
                    closeException = closeEx;
                }
            }
            throw (RuntimeException) addSuppressedOrLog(runException, closeException, "Exception closing new span.");
        }
    }

}
