package io.opentracing.contrib.global;

import io.opentracing.NoopTracer;
import io.opentracing.Tracer;
import io.opentracing.mock.MockTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

/**
 * @author Sjoerd Talsma
 */
public class GlobalTracerTest {

    Tracer previousGlobalTracer;

    @Before
    public void setup() {
        previousGlobalTracer = GlobalTracer.tracer();
    }

    @After
    public void teardown() {
        GlobalTracer.setTracer(previousGlobalTracer instanceof NoopTracer ? null : previousGlobalTracer);
    }

    /**
     * The MockTracer has been declared in the META-INF/services/io.opentracing.Tracer file,
     * so should be the default GlobalTracer instance in tests.
     */
    @Test
    public void testServiceLoading() {
        GlobalTracer.setTracer(null);
        assertThat(GlobalTracer.tracer(), is(instanceOf(MockTracer.class)));
    }

    /**
     * Setting an explicit tracer implementation should take preference of whatever the global tracer was at that time.
     */
    @Test
    public void testExplicitSetting() {
        Tracer t1 = Mockito.mock(Tracer.class), t2 = Mockito.mock(Tracer.class);
        GlobalTracer.setTracer(t1);
        assertThat(GlobalTracer.tracer(), is(sameInstance(t1)));
        assertThat(GlobalTracer.setTracer(t2), is(sameInstance(t1)));
        assertThat(GlobalTracer.tracer(), is(sameInstance(t2)));
    }

}
