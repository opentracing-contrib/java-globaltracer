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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Sjoerd Talsma
 */
public class GlobalTracerTest {

    Tracer previousGlobalTracer;

    @Before
    public void setup() {
        previousGlobalTracer = GlobalTracer.setTracer(null); // Reset lazy state and remember previous tracer.
    }

    @After
    public void teardown() {
        GlobalTracer.setTracer(previousGlobalTracer instanceof NoopTracer ? null : previousGlobalTracer);
    }

    @Test
    public void testSingletonReference() {
        Tracer tracer = GlobalTracer.tracer();
        assertThat(tracer, is(instanceOf(GlobalTracer.class)));
        assertThat(tracer, is(sameInstance(GlobalTracer.tracer())));
    }

    @Test
    public void testSettingGlobalTracerAsOwnDelegate() {
        try {
            GlobalTracer.setTracer(GlobalTracer.tracer());
            fail("exception expected");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage(), is(notNullValue()));
        }
    }

    /**
     * The MockTracer has been declared in the META-INF/services/io.opentracing.Tracer file,
     * so should be the default GlobalTracer instance in tests.
     */
    @Test
    public void testServiceLoading() {
        GlobalTracer.setTracer(null); // clear global tracer.
        GlobalTracer.tracer().buildSpan("some operation"); // trigger lazy tracer service loading.

        Tracer loadedTracer = GlobalTracer.setTracer(null); // clear again, return current (loaded) tracer.
        assertThat(loadedTracer, is(instanceOf(MockTracer.class))); // MockTracer was configured.
    }

    /**
     * Setting an explicit tracer implementation should take preference of whatever the global tracer was at that time.
     */
    @Test
    public void testExplicitSetting() {
        GlobalTracer.tracer().buildSpan("some operation"); // trigger lazy tracer service loading.
        Tracer t1 = mock(Tracer.class), t2 = mock(Tracer.class);
        when(t1.buildSpan(anyString())).thenReturn(NoopSpanBuilder.INSTANCE);
        when(t2.buildSpan(anyString())).thenReturn(NoopSpanBuilder.INSTANCE);

        GlobalTracer.setTracer(t1);
        GlobalTracer.tracer().buildSpan("first operation");
        GlobalTracer.tracer().buildSpan("second operation");

        assertThat(GlobalTracer.setTracer(t2), is(sameInstance(t1)));
        GlobalTracer.tracer().buildSpan("third operation");

        verify(t1).buildSpan("first operation");
        verify(t1).buildSpan("second operation");
        verifyNoMoreInteractions(t1);
        verify(t2).buildSpan("third operation");
        verifyNoMoreInteractions(t2);
    }

}
