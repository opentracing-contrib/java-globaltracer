# java-globaltracer
A tracer providing global ThreadLocal Span propagation, delegating to another Tracer implementation.

This library provides a utility-class to register a configured delegate tracer to become the implicit 'global' tracer instance available throughout the application.
If no Tracer is registered, the utility class will attempt to resolve it through the standard Java ContextLoader mechanism, looking for Tracer factory implementations that have declared themselves through an entry in the `"META-INF/services/io.opentracing.Tracer"` file.

The global tracer can be obtained by invoking the static `GlobalTracer.tracer()` method.
Even if no tracer was previously registered nor any tracer service factories were found, this static method will _always_ return a non-`null` Tracer implementation (opting for the no-op tracer by default).

The currently-active global Span can be obtained from a call to the static `GlobalTracer.activeSpan()` method.
Please note: this method may return `null` if no active span currently exists.

Starting a new span from the global tracer will automatically declare a 'child-of' reference to an active span if it exists.

Active spans are propagated to background threads automatically if the `ContextAwareExecutorService` is used.
It is also possible to programatically propagate a snapshot of all contexts to a new thread, activating it within the new thread. Active global Spans will automatically be included in such snapshots.
Please see the javadoc description of the `ContextManagers` utility class for more details on manually propagating snapshots.

The relation between these global tracer concepts and the Opentracing API are represented in the following UML diagram for this package:
<img src="http://www.plantuml.com/plantuml/png/fLPjRvim4FxUN-6FJ6dz0oQKgQsgQTAQI0j_mC01dWn6s6tMTVdlCuG4Ox1n4hxmzjpvVFVSFQv82Q8c2I3ACQ-XaWr9Q9NZXAj76kESyPWmz7btX_ehOKG8zBtx7YaqDEelqgvF8OcaoG6j6iYfaD04HzoNh-YTqzG2bXqcKA0ZsW8ZYQJliAj9rKBR-nbuC8DqxWdhMy2lf2BvP2ihMb592QD_OGFxPsKFVsJGBghMxb_3zdKzDij1FdPndaoiyCDyOblGqnjbwJGa8fuBzMERTN7qNizPmvhbs4vbi96JdCsuzF15qlGNPD10bK2mKqcjydjqMJXxAejT6r945GKbAUk0yIhtj8n8FgpRtQymZ3bduBLVqtBpLiOTdvSCOvBdYbS_99IYpRrwQ4ZC87mbDNvHTFm8nzjOUr73v4Goq4ohmXYJAIwB8fXhmjE_lW3J_mdZ0kPD1SXjhJObAQysQfe4vt9CUQQmiuLRH0wLMeIUIpD5ggvy6kgX1TezJBnEiSwG6Sy3LTLpFR-jhR0sp_V84xomvD7N0kYqp3R-3OdqycK3QwoaYcLDref7nR0aJ9H4y4pkIGEuPoJkgTYFrp1Qcs91Oj7EPtcm3Dj86vtHschhchYHtbEd9z-GKVWFuokKP4ZJI2fN3mytEHjoRdYPJY97fg_K4BkB2srlbW_TsxLwuFBXBGqk9xugOVguIXPr9_DIFt1_cJeTwIT7aO66fyLwPR8taomQ82dZYMBsOsyKTTz-jjzcMlHMNofsrMD3x1wzCoEfUZezr5M7EuHs1cRJt540LlaTLTrNNHr63g5rMDarLpVo4qZJmbShJJk_xViegB7nouu5XaRvdZ-CDNrFMFD9SEc-Ands8u1ba7HzP8oEO_lrfuQ2fe2WhEK7oYYmL9XVIv05JyL_">
