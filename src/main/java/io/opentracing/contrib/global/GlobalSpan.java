package io.opentracing.contrib.global;

import io.opentracing.NoopSpan;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import nl.talsmasoftware.context.Context;
import nl.talsmasoftware.context.threadlocal.AbstractThreadLocalContext;

import java.util.Map;
import java.util.Optional;

/**
 * This span delegates all its core methods to another span implementation, providing a global context.
 *
 * @author Sjoerd Talsma
 */
class GlobalSpan extends AbstractThreadLocalContext<Span> implements Span {
    /**
     * Threadlocal reference to enable our ContextManager to return the active span.
     */
    private static final ThreadLocal<GlobalSpan> ACTIVE = threadLocalInstanceOf(GlobalSpan.class);

    /**
     * Instantiates a new context with the specified value.
     * The new context will be made the active context for the current thread.
     *
     * @param delegate The new span to become the 'implicit active span' in this new context
     *                 (or <code>null</code> to register a new context with 'no active span').
     */
    GlobalSpan(Span delegate) {
        super(delegate);
    }

    /**
     * This method exists purely on behalf of {@link GlobalTracer.Manager#getActiveContext()}.
     *
     * @return The currently active context or <code>null</code> if no context is active.
     */
    static GlobalSpan activeContext() {
        return ACTIVE.get();
    }

    /**
     * This internal method to return a guaranteed non-<code>null</code> result is useful to implement
     * the method delegation without having to null-check in every call.
     *
     * @return The delegate span or the no-op span if no delegate is present.
     */
    private Span delegateOrNoop() {
        return Optional.ofNullable(value).orElse(NoopSpan.INSTANCE);
    }

    @Override
    public SpanContext context() {
        return delegateOrNoop().context();
    }

    /**
     * Finish delegates the call to the underlying {@link Span} and also {@link #close() closes} this
     * {@link Context global context}, restoring the active Span back to the parent span, if available.
     *
     * @see Span#finish()
     * @see Context#close()
     */
    @Override
    public void finish() {
        if (!isClosed()) try {
            delegateOrNoop().finish();
        } finally {
            super.close();
        }
    }

    /**
     * Finish delegates the call to the underlying {@link Span} and also {@link #close() closes} this
     * {@link Context global context}, restoring the active span back to the parent span.
     *
     * @param finishMicros an explicit finish time, in microseconds since the epoch
     * @see Span#finish(long)
     * @see Context#close()
     */
    @Override
    public void finish(long finishMicros) {
        if (!isClosed()) try {
            delegateOrNoop().finish(finishMicros);
        } finally {
            super.close();
        }
    }

    /**
     * Close delegates the call to the underlying {@link Span} and also {@link Context#close() closes} this
     * {@link Context global context}, restoring the active span back to its parent span.
     *
     * @see Span#close()
     * @see Context#close()
     */
    @Override
    public void close() {
        if (!isClosed()) try {
            delegateOrNoop().close();
        } finally {
            super.close();
        }
    }

    @Override
    public Span setOperationName(String operationName) {
        delegateOrNoop().setOperationName(operationName);
        return this;
    }

    @Override
    public Span setTag(String key, String value) {
        delegateOrNoop().setTag(key, value);
        return this;
    }

    @Override
    public Span setTag(String key, boolean value) {
        delegateOrNoop().setTag(key, value);
        return this;
    }

    @Override
    public Span setTag(String key, Number value) {
        delegateOrNoop().setTag(key, value);
        return this;
    }

    @Override
    public String getBaggageItem(String key) {
        return delegateOrNoop().getBaggageItem(key);
    }

    @Override
    public Span setBaggageItem(String key, String value) {
        delegateOrNoop().setBaggageItem(key, value);
        return this;
    }

    @Override
    public Span log(Map<String, ?> fields) {
        delegateOrNoop().log(fields);
        return this;
    }

    @Override
    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        delegateOrNoop().log(timestampMicroseconds, fields);
        return this;
    }

    @Override
    public Span log(String event) {
        delegateOrNoop().log(event);
        return this;
    }

    @Override
    public Span log(long timestampMicroseconds, String event) {
        delegateOrNoop().log(timestampMicroseconds, event);
        return this;
    }

    @Override
    @SuppressWarnings("deprecation") // We simply delegate this method as we're told.
    public Span log(String eventName, Object payload) {
        delegateOrNoop().log(eventName, payload);
        return this;
    }

    @Override
    @SuppressWarnings("deprecation") // We simply delegate this method as we're told.
    public Span log(long timestampMicroseconds, String eventName, Object payload) {
        delegateOrNoop().log(timestampMicroseconds, eventName, payload);
        return this;
    }

}
