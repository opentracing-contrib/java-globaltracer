package io.opentracing.contrib.global.concurrent;

import io.opentracing.NoopSpan;
import io.opentracing.NoopTracer;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.global.GlobalTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Sjoerd Talsma
 */
public class TracedCallableTest {

    Tracer previousGlobalTracer = null;
    Tracer mockTracer = null;
    ExecutorService threadpool;

    @Before
    public void setup() {
        previousGlobalTracer = GlobalTracer.tracer();
        mockTracer = mock(Tracer.class);
        threadpool = Executors.newCachedThreadPool();
        GlobalTracer.setTracer(mockTracer);
    }

    @After
    public void teardown() {
        GlobalTracer.setTracer(previousGlobalTracer instanceof NoopTracer ? null : previousGlobalTracer);
        threadpool.shutdown();
        verifyNoMoreInteractions(mockTracer);
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
    public void testNoTracingWithoutOperationName() throws ExecutionException, InterruptedException {
        Future<String> result = threadpool.submit(TracedCallable.of(new SimpleCallable()));

        // Block for result.
        assertThat(result.get(), is("called"));
        verifyNoMoreInteractions(mockTracer); // No interaction with tracer!
    }

    @Test
    public void testTracedCall() throws ExecutionException, InterruptedException {
        SpanBuilder mockSpanBuilder = mock(SpanBuilder.class);
        when(mockTracer.buildSpan(eq("testing"))).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.start()).thenReturn(NoopSpan.INSTANCE);

        Future<String> result = threadpool.submit(
                TracedCallable.of(new SimpleCallable()).withOperationName("testing"));

        // Block for result.
        assertThat(result.get(), is("called"));
        verify(mockSpanBuilder).start();
        verify(mockTracer).buildSpan("testing");
        verifyNoMoreInteractions(mockSpanBuilder);
    }

    @Test
    public void testTracedWithParentCall() throws ExecutionException, InterruptedException {
        final SpanContext mockParentContext = mock(SpanContext.class);
        final SpanBuilder mockSpanBuilder = mock(SpanBuilder.class);
        when(mockTracer.buildSpan(eq("testing"))).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.asChildOf(any(SpanContext.class))).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.start()).thenReturn(NoopSpan.INSTANCE);
        Future<String> result = threadpool.submit(
                TracedCallable.of(new SimpleCallable()).withOperationName("testing").asChildOf(mockParentContext));

        // Block for result.
        assertThat(result.get(), is("called"));
        verify(mockSpanBuilder).asChildOf(mockParentContext);
        verify(mockSpanBuilder).start();
        verify(mockTracer).buildSpan("testing");
        verifyNoMoreInteractions(mockParentContext);
        verifyNoMoreInteractions(mockSpanBuilder);
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

}
