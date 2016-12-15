package io.opentracing.contrib.global.thirdparty.propagation;

import io.opentracing.NoopSpan;
import io.opentracing.NoopTracer;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.global.ActiveSpanManager;
import io.opentracing.contrib.global.GlobalTracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import nl.talsmasoftware.context.executors.ContextAwareExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * This test is intended to show that a 'compatible' thread pool will actually propagate the global span into the
 * new thread.
 *
 * @author Sjoerd Talsma
 */
public class ThirdpartyPropagationTest {

    /**
     * A 'context-aware' thread-pool that knows that is needs to propagate ActiveSpan objects.
     */
    ExecutorService thirdpartyThreadpool;

    MockTracer mockTracer;
    Tracer previousGlobalTracer;

    @Before
    public void setUp() {
        thirdpartyThreadpool = new ContextAwareExecutorService(Executors.newCachedThreadPool());
        mockTracer = new MockTracer();
        previousGlobalTracer = GlobalTracer.register(mockTracer);
    }

    @After
    public void tearDown() {
        GlobalTracer.register(previousGlobalTracer instanceof NoopTracer ? null : previousGlobalTracer);
        thirdpartyThreadpool.shutdown();
    }

    @Test
    public void testSpanPropagation_newThread() throws ExecutionException, InterruptedException {
        // test code: outer span with an inner span in a background-thread.
        Future<?> future;
        Span span = GlobalTracer.tracer().buildSpan("testPropagation").start();
        try {
            span.setTag("username", "John Doe");
            future = thirdpartyThreadpool.submit(new Runnable() {
                public void run() {
                    Span span2 = GlobalTracer.tracer()
                            .buildSpan("threadedCall")
                            .asChildOf(ActiveSpanManager.activeSpan())
                            .start();
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

        assertThat(ActiveSpanManager.activeSpan(), is(instanceOf(NoopSpan.class)));

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
