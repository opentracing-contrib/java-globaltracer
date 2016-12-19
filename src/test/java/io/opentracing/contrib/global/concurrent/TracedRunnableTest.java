package io.opentracing.contrib.global.concurrent;

import io.opentracing.NoopSpan;
import io.opentracing.NoopTracer;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.global.GlobalTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Sjoerd Talsma
 */
public class TracedRunnableTest {

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
    public void testNoTracingWithoutOperationName() throws ExecutionException, InterruptedException {
        final AtomicBoolean ran = new AtomicBoolean(false);
        Future<?> result = threadpool.submit(TracedRunnable.of(new Runnable() {
            @Override
            public void run() {
                ran.set(true);
            }
        }));

        result.get(); // Block for result.
        assertThat(ran.get(), is(true));
        verifyNoMoreInteractions(mockTracer); // No interaction with tracer!
    }

    @Test
    public void testTracedCall() throws ExecutionException, InterruptedException {
        SpanBuilder mockSpanBuilder = mock(SpanBuilder.class);
        when(mockTracer.buildSpan(eq("testing"))).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.start()).thenReturn(NoopSpan.INSTANCE);

        final AtomicBoolean ran = new AtomicBoolean(false);
        Future<?> result = threadpool.submit(TracedRunnable.of(new Runnable() {
            @Override
            public void run() {
                ran.set(true);
            }
        }).withOperationName("testing"));

        result.get(); // Block for result.
        assertThat(ran.get(), is(true));
        verify(mockSpanBuilder).start();
        verify(mockTracer).buildSpan("testing");
        verifyNoMoreInteractions(mockSpanBuilder);
    }

}
