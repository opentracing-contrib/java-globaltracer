package io.opentracing.contrib.global.delegation;

import io.opentracing.Span;
import io.opentracing.SpanContext;

import java.util.Map;

/**
 * Abstract delegate {@link Span} that can be extended by concrete implementations to override individual methods.
 *
 * @author Sjoerd Talsma
 */
public abstract class DelegateSpan implements Span {

    /**
     * Non-<code>null</code> delegate span to forward all called methods to.
     */
    protected final Span delegate;

    public DelegateSpan(Span delegate) {
        if (delegate == null) throw new NullPointerException("Delegate span was <null>.");
        this.delegate = delegate;
    }

    public SpanContext context() {
        return delegate.context();
    }

    public void finish() {
        delegate.finish();
    }

    public void finish(long finishMicros) {
        delegate.finish(finishMicros);
    }

    public void close() {
        delegate.close();
    }

    public Span setTag(String key, String value) {
        delegate.setTag(key, value);
        return this;
    }

    public Span setTag(String key, boolean value) {
        delegate.setTag(key, value);
        return this;
    }

    public Span setTag(String key, Number value) {
        delegate.setTag(key, value);
        return this;
    }

    public Span log(Map<String, ?> fields) {
        delegate.log(fields);
        return this;
    }

    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        delegate.log(timestampMicroseconds, fields);
        return this;
    }

    public Span log(String event) {
        delegate.log(event);
        return this;
    }

    public Span log(long timestampMicroseconds, String event) {
        delegate.log(timestampMicroseconds, event);
        return this;
    }

    public Span setBaggageItem(String key, String value) {
        delegate.setBaggageItem(key, value);
        return this;
    }

    public String getBaggageItem(String key) {
        return delegate.getBaggageItem(key);
    }

    public Span setOperationName(String operationName) {
        delegate.setOperationName(operationName);
        return this;
    }

    @SuppressWarnings("deprecation") // We simply delegate this method as we're told.
    public Span log(String eventName, Object payload) {
        delegate.log(eventName, payload);
        return this;
    }

    @SuppressWarnings("deprecation") // We simply delegate this method as we're told.
    public Span log(long timestampMicroseconds, String eventName, Object payload) {
        delegate.log(timestampMicroseconds, eventName, payload);
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{delegate=" + delegate + '}';
    }
}
