package io.opentracing.contrib.global.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static io.opentracing.contrib.global.concurrent.SpanAwareExecutorService.traced;

/**
 * @author Sjoerd Talsma
 * @navassoc - delegatesTo - Executors
 */
public final class SpanAwareExecutors {

    /**
     * Private constructor to avoid instantiation of this utility class.
     */
    private SpanAwareExecutors() {
        throw new UnsupportedOperationException();
    }

    /**
     * This method returns a {@link Executors#newFixedThreadPool(int) fixed threadpool} that propagates
     * the active span into the started threads.
     *
     * @param nThreads the number of threads in the pool
     * @return the newly created thread pool
     * @see Executors#newFixedThreadPool(int)
     */
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return traced(Executors.newFixedThreadPool(nThreads));
    }

    /**
     * This method returns a {@link Executors#newFixedThreadPool(int, ThreadFactory) fixed threadpool} that propagates
     * the active span into the started threads.
     *
     * @param nThreads      the number of threads in the pool
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created thread pool
     * @see Executors#newFixedThreadPool(int, ThreadFactory)
     */
    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return traced(Executors.newFixedThreadPool(nThreads, threadFactory));
    }

    /**
     * This method returns a {@link Executors#newSingleThreadExecutor() single-threaded executor} that propagates
     * the active span into the started thread.
     *
     * @return the newly created single-theaded executor
     * @see Executors#newSingleThreadExecutor()
     */
    public static ExecutorService newSingleThreadExecutor() {
        return traced(Executors.newSingleThreadExecutor());
    }

    /**
     * This method returns a {@link Executors#newSingleThreadExecutor(ThreadFactory) single-threaded executor}
     * that propagates the active span into the started thread.
     *
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created single-theaded executor
     * @see Executors#newSingleThreadExecutor(ThreadFactory)
     */
    public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return traced(Executors.newSingleThreadExecutor(threadFactory));
    }

    /**
     * This method returns a {@link Executors#newCachedThreadPool() cached threadpool} that propagates
     * the active span into the started threads.
     *
     * @return the newly created thread pool
     * @see Executors#newCachedThreadPool()
     */
    public static ExecutorService newCachedThreadPool() {
        return traced(Executors.newCachedThreadPool());
    }

    /**
     * This method returns a {@link Executors#newCachedThreadPool(ThreadFactory) cached threadpool} that propagates
     * the active span into the started threads.
     *
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created thread pool
     * @see Executors#newCachedThreadPool(ThreadFactory)
     */
    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return traced(Executors.newCachedThreadPool(threadFactory));
    }

}
