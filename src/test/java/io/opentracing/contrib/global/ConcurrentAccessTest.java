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

import io.opentracing.NoopTracer;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static io.opentracing.contrib.global.GlobalTracerTest.sleepRandom;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class ConcurrentAccessTest {

    Tracer previousGlobalTracer;

    @Before
    public void setup() {
        previousGlobalTracer = GlobalTracer.register(null); // Reset lazy state and remember previous tracer.
    }

    @After
    public void teardown() {
        GlobalTracer.register(previousGlobalTracer instanceof NoopTracer ? null : previousGlobalTracer);
    }

    @Test
    public void testConcurrent_readWrites() throws InterruptedException {
        final MockTracer original = new MockTracer();
        GlobalTracer.register(original);

        final int count = 10;
        final Thread[] threads = new Thread[3 * count];

        // Threads re-registration of original
        for (int i = 0; i < count; i++)
            threads[i] = new Thread() {
                public void run() {
                    sleepRandom(100);
                    GlobalTracer.register(original);
                }
            };
        // Threads adding trivial wrappers
        for (int i = count; i < 2 * count; i++) {
            threads[i] = new Thread() {
                public void run() {
                    GlobalTracer.update(TrivialWrappingTracer.WRAP);
                }
            };
        }
        for (int i = 2 * count; i < 3 * count; i++) {
            threads[i] = new Thread() {
                public void run() {
                    Span span = GlobalTracer.get().buildSpan("operation").start();
                    sleepRandom(100);
                    span.close();
                }
            };
        }

        // Start threads & wait for completion
        Collections.shuffle(asList(threads));
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join(1000);

        assertThat(original.finishedSpans(), hasSize(count));
        for (MockSpan span : original.finishedSpans()) {
            assertThat(span.operationName(), is("operation"));
        }
    }

}
