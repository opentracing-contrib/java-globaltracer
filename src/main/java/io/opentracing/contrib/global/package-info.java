/*
 * Copyright (C) 2016 Talsma ICT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/**
 * This package provides a utility-class to
 * <a href="GlobalTracer.html#register-io.opentracing.Tracer-">register</a>
 * a configured delegate tracer to become the implicit 'global' tracer instance available throughout the application.
 * <p>
 * If no {@link io.opentracing.Tracer Tracer} is explicitly registered, the utility class will attempt to
 * resolve it through the standard Java <code>ServiceLoader</code> mechanism, looking for Tracer factory
 * implementations that have declared themselves through an entry in the
 * <code>"META-INF/services/io.opentracing.Tracer"</code> file.
 * <p>
 * The global tracer can be obtained by invoking the static
 * <a href="GlobalTracer.html#tracer--">tracer()</a> method.<br>
 * Even if no tracer was previously registered nor any tracer service factories were found,
 * this static method will <em>always</em> return a non-<code>null</code> Tracer implementation
 * (opting for the no-op tracer by default).
 * <p>
 * The currently-active global {@link io.opentracing.Span Span} can be obtained from a call to the static
 * <a href="GlobalSpanManager.html#activeSpan--">activeSpan()</a> method.<br>
 * <em>Please note:</em> this method may return <code>null</code> if no active span currently exists.
 * <p>
 * Starting a new span from the global tracer will automatically declare a 'child of' reference
 * to any active span if it exists.
 * <p>
 * The relation between these concepts and the Opentracing API are represented in the following class diagram
 * for this package:<br>
 * <center><img src="package.svg" alt="Package classes"></center>
 *
 * @author Sjoerd Talsma
 */
package io.opentracing.contrib.global;