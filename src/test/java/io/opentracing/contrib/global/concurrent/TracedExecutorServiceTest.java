package io.opentracing.contrib.global.concurrent;

import io.opentracing.NoopTracer;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.global.GlobalSpanManager;
import io.opentracing.contrib.global.GlobalTracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author Sjoerd Talsma
 */
public class TracedExecutorServiceTest {

    ExecutorService tracedThreadpool;
    Tracer oldDelegate;
    MockTracer mockTracer;

    @Before
    public void setUp() {
        oldDelegate = GlobalTracer.tracer();
        tracedThreadpool = TracedExecutors.newCachedThreadPool();
        mockTracer = new MockTracer();
        GlobalTracer.register(mockTracer);
    }

    @After
    public void tearDown() {
        GlobalTracer.register(oldDelegate instanceof NoopTracer ? null : oldDelegate);
        tracedThreadpool.shutdown();
    }

    @Test
    public void testSpanPropagation_newThread() throws ExecutionException, InterruptedException {
        // test code: outer span with an inner span in a background-thread.
        Future<?> future;
        Span span = GlobalTracer.tracer().buildSpan("testPropagation").start();
        try {
            span.setTag("username", "John Doe");
            future = tracedThreadpool.submit(new Runnable() {
                public void run() {
                    Span span2 = GlobalTracer.tracer().buildSpan("threadedCall").start();
                    try {
                        span2.setTag("monkeys", 12);
                        Thread.sleep(150L); // let it actually finish after the outer span.
                    } catch (InterruptedException e) {
                        throw new IllegalStateException("sleep interrupted!", e);
                    } finally {
                        span2.close();
                    }
                }
            });
        } finally {
            span.close();
        }
        future.get(); // block until background tasks completes.

        assertThat(GlobalSpanManager.activeSpan(), is(nullValue()));

        List<MockSpan> finishedSpans = mockTracer.finishedSpans();
        assertThat(finishedSpans, hasSize(2));
        final MockSpan span1 = finishedSpans.get(0);
        final MockSpan span2 = finishedSpans.get(1);
        assertThat(span1.operationName(), is("testPropagation"));
        assertThat(span1.parentId(), is(0L));
        assertThat(span2.operationName(), is("threadedCall"));
        // Important: span(1) should have span(0) as its parent!
        assertThat(span2.parentId(), is(span1.context().spanId()));
    }

}
