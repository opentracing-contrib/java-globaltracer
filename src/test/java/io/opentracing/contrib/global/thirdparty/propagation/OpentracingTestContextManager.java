package io.opentracing.contrib.global.thirdparty.propagation;

import io.opentracing.NoopSpan;
import io.opentracing.Span;
import io.opentracing.contrib.global.ActiveSpanManager;
import io.opentracing.contrib.global.ActiveSpanManager.SpanDeactivator;
import nl.talsmasoftware.context.Context;
import nl.talsmasoftware.context.ContextManager;

/**
 * (too) simple implementation to demonstrate linking a general context-propagation mechanism to the
 * {@link ActiveSpanManager}.
 *
 * @author Sjoerd Talsma
 */
public class OpentracingTestContextManager implements ContextManager<Span> {

    public Context<Span> initializeNewContext(final Span value) {
        return new Context<Span>() {
            final SpanDeactivator deactivator = ActiveSpanManager.activate(value);

            public Span getValue() {
                return value;
            }

            public void close() {
                try {
                    deactivator.deactivate();
                } catch (Exception closeException) {
                    throw new IllegalStateException("Exception while deactivating global span.", closeException);
                }
            }
        };
    }

    public Context<Span> getActiveContext() {
        final Span activeSpan = ActiveSpanManager.activeSpan();
        return activeSpan == null || activeSpan instanceof NoopSpan ? null : new Context<Span>() {
            public Span getValue() {
                return activeSpan;
            }

            public void close() {
                if (activeSpan != null) try {
                    activeSpan.close();
                } catch (Exception closeException) {
                    throw new IllegalStateException("Exception while closing the active span.", closeException);
                }
            }
        };
    }

}
