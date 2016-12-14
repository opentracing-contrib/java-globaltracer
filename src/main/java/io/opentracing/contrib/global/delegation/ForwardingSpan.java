package io.opentracing.contrib.global.delegation;

import io.opentracing.*;

import java.util.Map;

/**
 * Abstract delegate {@link Span} that can be extended by concrete implementations to override individual methods.
 *
 * @author Sjoerd Talsma
 */
abstract class ForwardingSpan implements Span {

    /**
     * Non-<code>null</code> delegate span to forward all called methods to.
     */
    protected Span delegate;

    ForwardingSpan(Span delegate) {
        if (delegate == null) throw new NullPointerException("Delegate span was <null>.");
        this.delegate = delegate;
    }

    protected Span rewrap(Span span) {
        if (span == null || span instanceof NoopSpan) return NoopSpan.INSTANCE;
        this.delegate = span;
        return this;
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
        return rewrap(delegate.setTag(key, value));
    }

    public Span setTag(String key, boolean value) {
        return rewrap(delegate.setTag(key, value));
    }

    public Span setTag(String key, Number value) {
        return rewrap(delegate.setTag(key, value));
    }

    public Span log(Map<String, ?> fields) {
        return rewrap(delegate.log(fields));
    }

    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        return rewrap(delegate.log(timestampMicroseconds, fields));
    }

    public Span log(String event) {
        return rewrap(delegate.log(event));
    }

    public Span log(long timestampMicroseconds, String event) {
        return rewrap(delegate.log(timestampMicroseconds, event));
    }

    public Span setBaggageItem(String key, String value) {
        return rewrap(delegate.setBaggageItem(key, value));
    }

    public String getBaggageItem(String key) {
        return delegate.getBaggageItem(key);
    }

    public Span setOperationName(String operationName) {
        return rewrap(delegate.setOperationName(operationName));
    }

    @SuppressWarnings("deprecation") // We simply delegate this method as we're told.
    public Span log(String eventName, Object payload) {
        return rewrap(delegate.log(eventName, payload));
    }

    @SuppressWarnings("deprecation") // We simply delegate this method as we're told.
    public Span log(long timestampMicroseconds, String eventName, Object payload) {
        return rewrap(delegate.log(timestampMicroseconds, eventName, payload));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{delegate=" + delegate + '}';
    }
}
