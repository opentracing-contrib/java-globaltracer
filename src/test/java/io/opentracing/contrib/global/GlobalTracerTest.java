package io.opentracing.contrib.global;

import io.opentracing.NoopSpanBuilder;
import io.opentracing.NoopTracer;
import io.opentracing.Tracer;
import io.opentracing.mock.MockTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class GlobalTracerTest {

    Tracer previousGlobalTracer;

    @Before
    public void setup() {
        previousGlobalTracer = GlobalTracer.set(null); // Reset lazy state and remember previous tracer.
    }

    @After
    public void teardown() {
        GlobalTracer.set(previousGlobalTracer instanceof NoopTracer ? null : previousGlobalTracer);
    }

    @Test
    public void testGet_SingletonReference() {
        Tracer tracer = GlobalTracer.get();
        assertThat(tracer, is(instanceOf(GlobalTracer.class)));
        assertThat(tracer, is(sameInstance(GlobalTracer.get())));
    }

    /**
     * The MockTracer has been declared in the META-INF/services/io.opentracing.Tracer file,
     * so should be the default GlobalTracer instance in tests.
     */
    @Test
    public void testGet_AutomaticServiceLoading() {
        GlobalTracer.get().buildSpan("some operation"); // triggers lazy tracer resolution.
        Tracer resolvedTracer = GlobalTracer.set(null); // clear again, returning current (auto-resolved) tracer.
        assertThat("Resolved Tracer service", resolvedTracer, is(instanceOf(MockTracer.class)));
    }

    /**
     * Setting an explicit tracer implementation should take preference of whatever the global tracer was at that time.
     */
    @Test
    public void testGet_AfterSets() {
        GlobalTracer.get().buildSpan("some operation"); // trigger lazy tracer service loading.
        Tracer t1 = mock(Tracer.class), t2 = mock(Tracer.class);
        when(t1.buildSpan(anyString())).thenReturn(NoopSpanBuilder.INSTANCE);
        when(t2.buildSpan(anyString())).thenReturn(NoopSpanBuilder.INSTANCE);

        GlobalTracer.set(t1);
        GlobalTracer.get().buildSpan("first operation");
        GlobalTracer.get().buildSpan("second operation");

        assertThat(GlobalTracer.set(t2), is(sameInstance(t1)));
        GlobalTracer.get().buildSpan("third operation");

        verify(t1).buildSpan(eq("first operation"));
        verify(t1).buildSpan(eq("second operation"));
        verify(t2).buildSpan(eq("third operation"));
        verifyNoMoreInteractions(t1, t2);
    }

    /**
     * Setting the GlobalTracer as its own delegate should be a no-op.
     */
    @Test
    public void testSet_GlobalTracerAsItsOwnDelegate() {
        Tracer result1 = GlobalTracer.set(GlobalTracer.get());
        Tracer result2 = GlobalTracer.set(GlobalTracer.get());
        assertThat(result1, is(sameInstance(previousGlobalTracer)));
        assertThat(result2, is(sameInstance(previousGlobalTracer)));
    }

    @Test
    public void testSet_ConcurrentThreads() throws InterruptedException {
        final int threadCount = 10;
        final Tracer[] tracers = new Tracer[threadCount];
        final Tracer[] previous = new Tracer[threadCount];
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) tracers[i] = mock(Tracer.class);
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            threads[idx] = new Thread() {
                @Override
                public void run() {
                    previous[idx] = GlobalTracer.set(tracers[idx]);
                }
            };
        }

        assertThat("Nothing happened yet", identityCount(null, previous), is(threadCount));
        // Start threads & wait
        for (int i = 0; i < threadCount; i++) threads[i].start();
        for (int i = 0; i < threadCount; i++) threads[i].join(1000);

        final Tracer last = GlobalTracer.set(null); // last-set tracer ('previous' of new reset).
        assertThat("Previous of first is null", identityCount(null, previous), is(1));
        assertThat("Last must be from tracers", identityCount(last, tracers), is(1));
        for (int i = 0; i < threadCount; i++) {
            if (last != tracers[i]) { // All non-last tracers should be in previous once!
                assertThat("Occurrences in previous", identityCount(tracers[i], previous), is(1));
            }
        }
    }

    private static int identityCount(Tracer needle, Tracer... haystack) {
        int count = 0;
        for (Tracer t : haystack) if (t == needle) count++;
        return count;
    }

}
