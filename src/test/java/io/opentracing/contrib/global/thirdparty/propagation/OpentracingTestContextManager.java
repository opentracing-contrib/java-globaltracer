package io.opentracing.contrib.global.thirdparty.propagation;

import io.opentracing.NoopSpan;
import io.opentracing.Span;
import io.opentracing.contrib.global.GlobalSpanManager;
import nl.talsmasoftware.context.Context;
import nl.talsmasoftware.context.ContextManager;

import java.io.Closeable;

/**
 * (too) simple implementation to demonstrate linking a general context-propagation mechanism to the
 * {@link GlobalSpanManager}.
 *
 * @author Sjoerd Talsma
 */
public class OpentracingTestContextManager implements ContextManager<Span> {

    public Context<Span> initializeNewContext(final Span value) {
        return new Context<Span>() {
            final Closeable deactivator = GlobalSpanManager.activate(value);

            public Span getValue() {
                return value;
            }

            public void close() {
                try {
                    deactivator.close();
                } catch (Exception closeException) {
                    throw new IllegalStateException("Exception while deactivating global span.", closeException);
                }
            }
        };
    }

    public Context<Span> getActiveContext() {
        final Span activeSpan = GlobalSpanManager.activeSpan();
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
