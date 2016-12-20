package io.opentracing.contrib.global.concurrent;

import io.opentracing.NoopTracer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.global.GlobalTracer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class TracedCallableTest {
    // standard threadpool
    private static ExecutorService threadpool = Executors.newCachedThreadPool();

    private Tracer previousGlobalTracer = null;

    private Tracer mockTracer = null;
    private SpanContext mockParentContext = null;
    private SpanBuilder mockSpanBuilder = null;
    private Span mockSpan = null;

    @Before
    public void setup() {
        mockTracer = mock(Tracer.class);
        previousGlobalTracer = GlobalTracer.setTracer(mockTracer);

        mockSpan = mock(Span.class);
        mockParentContext = mock(SpanContext.class);
        mockSpanBuilder = mock(SpanBuilder.class);
        when(mockTracer.buildSpan(anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.asChildOf(any(SpanContext.class))).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.start()).thenReturn(mockSpan);
    }

    @After
    public void teardown() {
        GlobalTracer.setTracer(previousGlobalTracer instanceof NoopTracer ? null : previousGlobalTracer);
        verifyNoMoreInteractions(mockTracer, mockSpanBuilder, mockParentContext, mockSpan);
    }

    @AfterClass
    public static void shutdownThreadpool() {
        threadpool.shutdown();
    }

    @Test
    public void testOfNullDelegate() {
        try {
            TracedCallable.of(null);
            fail("Exception expected");
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage(), not(nullValue()));
        }
    }

    @Test
    public void testWithoutOperationName() throws ExecutionException, InterruptedException {
        Future<String> result = threadpool.submit(TracedCallable.of(new SimpleCallable()));

        assertThat(result.get(), is("called"));
        verify(mockTracer).buildSpan(""); // No operation name was provided.
        verify(mockSpanBuilder).start();
        verify(mockSpan).close();
    }

    @Test
    public void testWithoutParentContext() throws ExecutionException, InterruptedException {
        Future<String> result = threadpool.submit(
                TracedCallable.of(new SimpleCallable()).withOperationName("testing"));

        assertThat(result.get(), is("called"));
        verify(mockTracer).buildSpan("testing");
        verify(mockSpanBuilder).start();
        verify(mockSpan).close();
    }

    @Test
    public void testWithParentContext() throws ExecutionException, InterruptedException {
        Future<String> result = threadpool.submit(
                TracedCallable.of(new SimpleCallable()).withOperationName("testing").asChildOf(mockParentContext));

        // Block for result.
        assertThat(result.get(), is("called"));
        verify(mockTracer).buildSpan("testing");
        verify(mockSpanBuilder).asChildOf(mockParentContext);
        verify(mockSpanBuilder).start();
        verify(mockSpan).close();
    }

    @Test
    public void testFailingCall() throws ExecutionException, InterruptedException {
        Future<String> result = threadpool.submit(
                TracedCallable.of(new FailingCallable()).withOperationName("testing"));

        try {
            result.get();
            fail("Call exception expected.");
        } catch (ExecutionException expected) { // Test for original exception!
            assertThat(expected.getCause(), is(instanceOf(UnsupportedOperationException.class)));
            assertThat(expected.getCause().getMessage(), is("Failure in call."));
        }

        verify(mockSpan).close(); // Close must still be called!
        verify(mockSpanBuilder).start();
        verify(mockTracer).buildSpan("testing");
    }

    @Test
    public void testFailingSpanClose() throws Exception {
        reset(mockSpan);
        doThrow(new IllegalStateException("Already closed.")).when(mockSpan).close();
        Future<String> result = threadpool.submit(
                TracedCallable.of(new SimpleCallable()).withOperationName("testing"));

        try {
            result.get();
            fail("Span.close exception expected.");
        } catch (ExecutionException expected) { // Test for close exception!
            assertThat(expected.getCause(), is(instanceOf(IllegalStateException.class)));
            assertThat(expected.getCause().getMessage(), is("Already closed."));
        }

        verify(mockSpan).close();
        verify(mockSpanBuilder).start();
        verify(mockTracer).buildSpan("testing");
    }

    @Test
    public void testExceptionsFromCallAndSpanClose() throws Exception {
        reset(mockSpan);
        doThrow(new IllegalStateException("Already closed.")).when(mockSpan).close();
        Future<String> result = threadpool.submit(
                TracedCallable.of(new FailingCallable()).withOperationName("testing"));

        try {
            result.get();
            fail("Call exception expected.");
        } catch (ExecutionException expected) { // Thrown exception must not shadow original exception!
            assertThat(expected.getCause(), is(instanceOf(UnsupportedOperationException.class)));
            assertThat(expected.getCause().getMessage(), is("Failure in call."));
        }

        verify(mockSpan).close();
        verify(mockSpanBuilder).start();
        verify(mockTracer).buildSpan("testing");
    }

    /**
     * Simple callable that returns "called".
     */
    private static class SimpleCallable implements Callable<String> {
        @Override
        public String call() throws Exception {
            return "called";
        }
    }

    /**
     * Simple callable that throws IllegalStateException.
     */
    private static class FailingCallable implements Callable<String> {
        @Override
        public String call() throws Exception {
            throw new UnsupportedOperationException("Failure in call.");
        }
    }

}
