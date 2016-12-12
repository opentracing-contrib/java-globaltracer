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
 * This package contains common <code>delegation</code> classes that provide no functionality of their own other than
 * forwarding all method calls to a provided delegate implementation.<br>
 * Since this code is <strong>not</strong> related to global tracing at all, maybe these classes could be moved to
 * a more generic java library (or even the API itself)?<br>
 * This is a very unfortunate side-effect of Java making it difficult for us to extend behaviour of concrete classes,
 * unlike other modern languages that provide mix-in support.
 * <p>
 * All classes in this package should be declared <em>abstract</em> although they implement the complete interfaces,
 * due to the fact that no functionality is provided. Therefore it would make no sense to instantiate an unmodified
 * delegat object.
 *
 * @author Sjoerd Talsma
 */
package io.opentracing.contrib.global.delegation;