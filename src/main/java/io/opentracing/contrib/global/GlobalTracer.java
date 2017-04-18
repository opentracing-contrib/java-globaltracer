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
import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Forwards all methods to another tracer that can be configured in one of two ways:
 * <ol>
 * <li>Explicitly, calling {@link #register(Tracer)} with a configured tracer, or:</li>
 * <li>Automatically using the Java {@link ServiceLoader} SPI mechanism to load a {@link Tracer} from the classpath.</li>
 * </ol>
 * <p>
 * When the tracer is needed it is lazily looked up using the following rules:
 * <ol type="a">
 * <li>The {@link #register(Tracer) registered} tracer always takes precedence.</li>
 * <li>If no tracer was registered, one is looked up from the {@link ServiceLoader}.<br>
 * The {@linkplain GlobalTracer} will not attempt to choose between implementations:</li>
 * <li>If no single tracer service is found, the {@link io.opentracing.NoopTracer NoopTracer} will be used.</li>
 * </ol>
 *
 * @see io.opentracing.util.GlobalTracer
 * @deprecated GlobalTracer is now part of OpenTracing-java
 */
public final class GlobalTracer {
    private static final Logger LOGGER = Logger.getLogger(GlobalTracer.class.getName());

    private static final AtomicBoolean SINGLE_INIT = new AtomicBoolean(false);

    private GlobalTracer() {
    }

    private static void lazyInit() {
        if (SINGLE_INIT.compareAndSet(false, true)) {
            final Tracer resolved = loadSingleSpiImplementation();
            if (!(resolved instanceof NoopTracer)) {
                try {
                    io.opentracing.util.GlobalTracer.register(resolved);
                    LOGGER.log(Level.INFO, "Using GlobalTracer: {0}.", resolved);
                } catch (RuntimeException alreadyRegistered) {
                    LOGGER.log(Level.WARNING, "Could not automatically register " + resolved + " because: "
                            + alreadyRegistered.getMessage(), alreadyRegistered);
                }
            }
        }
    }

    /**
     * Returns the constant {@linkplain GlobalTracer}.
     * <p>
     * All methods are forwarded to the currently configured tracer.<br>
     * Until a tracer is {@link #register(Tracer) explicitly configured},
     * one is looked up from the {@link ServiceLoader},
     * falling back to the {@link io.opentracing.NoopTracer NoopTracer}.<br>
     * A tracer can be re-configured at any time.
     * For example, the tracer used to extract a span may be different than the one that injects it.
     *
     * @return The global tracer constant.
     * @see io.opentracing.util.GlobalTracer#get()
     * @deprecated Please use GlobalTracer from opentracing-util instead.
     */
    public static Tracer get() {
        lazyInit();
        return io.opentracing.util.GlobalTracer.get();
    }

    /**
     * Explicitly configures a {@link Tracer} to back the behaviour of the {@link #get() global tracer}.
     * <p>
     * <strong>note:</strong> The previous global tracer is not returned anymore
     * because the GlobalTracer can only be registered once, therefore can no longer be restored to its previous value.
     *
     * @param tracer Tracer to use as global tracer.
     * @return Always <code>null</code>.
     */
    public static Tracer register(final Tracer tracer) {
        io.opentracing.util.GlobalTracer.register(tracer);
        LOGGER.log(Level.INFO, "Registered GlobalTracer {0}.", tracer);
        return null; // no way to return the previous instance
    }

    /**
     * Loads a single service implementation from {@link ServiceLoader}.
     *
     * @return The single service or a NoopTracer.
     */
    private static Tracer loadSingleSpiImplementation() {
        // Use the ServiceLoader to find the declared Tracer implementation.
        Iterator<Tracer> spiImplementations =
                ServiceLoader.load(Tracer.class, Tracer.class.getClassLoader()).iterator();
        if (spiImplementations.hasNext()) {
            Tracer foundImplementation = spiImplementations.next();
            if (!spiImplementations.hasNext()) {
                return foundImplementation;
            }
            LOGGER.log(Level.WARNING, "More than one Tracer service found. " +
                    "Falling back to NoopTracer implementation.");
        }
        return NoopTracerFactory.create();
    }

}
