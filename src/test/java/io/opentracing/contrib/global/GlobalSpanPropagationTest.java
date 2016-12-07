package io.opentracing.contrib.global;

import io.opentracing.NoopTracer;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import nl.talsmasoftware.context.executors.ContextAwareExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.opentracing.contrib.global.GlobalTracer.tracer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * This test is intended to show that a 'compatible' thread pool will actually propagate the global span into the
 * new thread.
 *
 * @author Sjoerd Talsma
 */
public class GlobalSpanPropagationTest {

    /**
     * A 'context-aware' thread-pool that knows that is needs to propagate GlobalSpan objects.
     */
    ExecutorService threadpool;

    Tracer oldDelegate;
    MockTracer tracer;

    @Before
    public void setUp() {
        oldDelegate = tracer();
        threadpool = new ContextAwareExecutorService(Executors.newCachedThreadPool());
        tracer = new MockTracer();
        GlobalTracer.registerDelegate(tracer);
    }

    @After
    public void tearDown() {
        GlobalTracer.registerDelegate(oldDelegate instanceof NoopTracer ? null : oldDelegate);
    }

    @Test
    @Ignore // Unfortunately there's still a bug in GlobalSpan close() somewhere..
    public void testSpanPropagation_newThread() throws ExecutionException, InterruptedException {
        // test code: outer span with an inner span in a background-thread.
        Future<?> future;
        try (Span span = tracer().buildSpan("testPropagation").start()) {
            span.setTag("username", "John Doe");
            future = threadpool.submit(() -> {
                try (Span span2 = tracer().buildSpan("threadedCall").start()) {
                    span2.setTag("monkeys", 12);
                    Thread.sleep(150L); // let it actually finish after the outer span.
                } catch (InterruptedException e) {
                    throw new IllegalStateException("sleep interrupted!", e);
                }
            });
        }
        future.get(); // block until background tasks completes.

        assertThat(GlobalTracer.activeSpan().isPresent(), is(false));

        List<MockSpan> finishedSpans = tracer.finishedSpans();
        // TODO: Fix bug testPropagation span gets closed from outside (ok!)
        //   ... but! also from the thread completion (which is normally ok as well, except in this case.. ?!?)
        //
        // (or is it?) quite a pickle, from the outside I definately would not expect this behaviour.
        // I want the Span to propagate into the thread, but the close() should just restore any previous span,
        // but NOT close the _actual_ span in this case, since it was not originally created in the thread!
        // --> Solution: do not close actual span objects from the immediate Snaphot, merely their Context...
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
