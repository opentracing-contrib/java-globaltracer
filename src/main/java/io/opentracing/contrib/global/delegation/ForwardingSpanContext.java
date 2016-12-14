package io.opentracing.contrib.global.delegation;

import io.opentracing.SpanContext;

import java.util.Map;

/**
 * {@link SpanContext} that forwards all methods to a delegate.
 *
 * @author Sjoerd Talsma
 */
abstract class ForwardingSpanContext implements SpanContext {

    protected SpanContext delegate;

    ForwardingSpanContext(SpanContext delegate) {
        if (delegate == null) throw new NullPointerException("The delegate SpanContext implementation is <null>.");
        this.delegate = delegate;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return delegate.baggageItems();
    }
}
