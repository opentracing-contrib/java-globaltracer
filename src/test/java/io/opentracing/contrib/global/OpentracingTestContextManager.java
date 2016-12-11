package io.opentracing.contrib.global;

import io.opentracing.NoopSpan;
import io.opentracing.Span;
import nl.talsmasoftware.context.Context;
import nl.talsmasoftware.context.ContextManager;

/**
 * @author Sjoerd Talsma
 */
public class OpentracingTestContextManager implements ContextManager<Span> {

    public Context<Span> initializeNewContext(final Span value) {
        return new Context<Span>() {
            final GlobalSpanManager.GlobalSpanDeactivator deactivator = GlobalSpanManager.activate(value);

            public Span getValue() {
                return value;
            }

            public void close() {
                deactivator.close();
            }
        };
    }

    public Context<Span> getActiveContext() {
        final Span activeSpan = GlobalSpanManager.activeSpan();
        return activeSpan == null || activeSpan instanceof NoopSpan ? null
                : new Context<Span>() {
            public Span getValue() {
                return activeSpan;
            }

            public void close() {
                throw new UnsupportedOperationException("Can't access the internal state to restore parent span.");
            }
        };
    }

}
