package io.opentracing.contrib.global;

import nl.talsmasoftware.context.executors.ContextAwareExecutorService;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final ExecutorService THREADPOOL = new ContextAwareExecutorService(Executors.newCachedThreadPool());

    @Test
    public void testSpanPropagation_newThread() {
        // todo
    }

}
