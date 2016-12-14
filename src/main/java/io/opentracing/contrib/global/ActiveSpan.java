package io.opentracing.contrib.global;

import io.opentracing.NoopSpan;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.contrib.global.ActiveSpanManager.SpanDeactivator;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of an 'active span'.<br>
 * This active span will deactivate itself after it has been finished, or closed.<br>
 * All other span functionality is forwareded to the delegate Span.
 *
 * @author Sjoerd Talsma
 */
final class ActiveSpan implements Span {
    private static final Logger LOGGER = Logger.getLogger(ActiveSpan.class.getName());

    protected Span delegate;
    private final SpanDeactivator deactivator;
    private final AtomicBoolean deactivated = new AtomicBoolean(false);

    ActiveSpan(Span delegate, SpanDeactivator deactivator) {
        if (delegate == null) throw new NullPointerException("Delegate span was <null>.");
        this.delegate = delegate;
        this.deactivator = deactivator;
    }

    protected Span rewrap(Span span) {
        if (span == null || span instanceof NoopSpan) return NoopSpan.INSTANCE;
        this.delegate = span;
        return this;
    }

    void deactivate() {
        if (deactivated.compareAndSet(false, true)) try {
            deactivator.deactivate();
        } catch (Exception deactivationException) {
            LOGGER.log(Level.WARNING, "Exception deactivating {0}.", new Object[]{this, deactivationException});
        }
    }

    public void finish() {
        try {
            delegate.finish();
        } finally {
            this.deactivate();
        }
    }

    public void finish(long finishMicros) {
        try {
            delegate.finish(finishMicros);
        } finally {
            this.deactivate();
        }
    }

    public void close() {
        try {
            delegate.close();
        } finally {
            this.deactivate();
        }
    }

    // Default behaviour is forwarded to the delegate Span:

    public SpanContext context() {
        return delegate.context();
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
