/**
 * Copyright 2017 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.global;

import io.opentracing.NoopSpanBuilder;
import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.mock.MockTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class GlobalTracerTest {

    @Before
    @After
    public void unregisterGlobalTracer() throws Exception {
        Field tracerFld = io.opentracing.util.GlobalTracer.class.getDeclaredField("tracer");
        Field singleInit = GlobalTracer.class.getDeclaredField("SINGLE_INIT");
        synchronized (tracerFld) {
            tracerFld.setAccessible(true);
            singleInit.setAccessible(true);
            try {
                tracerFld.set(null, NoopTracerFactory.create());
                ((AtomicBoolean) singleInit.get(null)).set(false);
            } finally {
                tracerFld.setAccessible(false);
                singleInit.setAccessible(false);
            }
        }
    }

    private static Tracer globalTracerInstance() {
        try {
            Field tracerFld = io.opentracing.util.GlobalTracer.class.getDeclaredField("tracer");
            synchronized (tracerFld) {
                tracerFld.setAccessible(true);
                try {
                    return (Tracer) tracerFld.get(null);
                } finally {
                    tracerFld.setAccessible(false);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGet_SingletonReference() {
        Tracer tracer = GlobalTracer.get();
        assertThat(tracer, is(instanceOf(io.opentracing.util.GlobalTracer.class)));
        assertThat(tracer, is(sameInstance(GlobalTracer.get())));
    }

    /**
     * The MockTracer has been declared in the META-INF/services/io.opentracing.Tracer file,
     * so should be the default GlobalTracer instance in tests.
     */
    @Test
    public void testGet_AutomaticServiceLoading() {
        GlobalTracer.get().buildSpan("some operation"); // triggers lazy tracer resolution.
        Tracer resolvedTracer = globalTracerInstance();
        assertThat("Resolved Tracer service", resolvedTracer, is(instanceOf(MockTracer.class)));
    }

    /**
     * Registering an explicit tracer implementation should take precedence, no matter what the global tracer was before.
     */
    @Test
    public void testGet_AfterRegister() {
        Tracer t1 = mock(Tracer.class);
        when(t1.buildSpan(anyString())).thenReturn(NoopSpanBuilder.INSTANCE);

        GlobalTracer.register(t1);
        GlobalTracer.get().buildSpan("first operation");
        GlobalTracer.get().buildSpan("second operation");

        verify(t1).buildSpan(eq("first operation"));
        verify(t1).buildSpan(eq("second operation"));
        verifyNoMoreInteractions(t1);
    }

    /**
     * Registering the GlobalTracer as its own delegate should be a no-op.
     */
    @Test
    public void testRegister_GlobalTracerAsItsOwnDelegate() {
        Tracer result1 = GlobalTracer.register(GlobalTracer.get());
        Tracer result2 = GlobalTracer.register(GlobalTracer.get());
        assertThat(result1, is(instanceOf(MockTracer.class))); // get() triggered lazy init
        assertThat(result2, is(sameInstance(result2)));
    }

}
