# java-globaltracer
Global ThreadLocal Span for implicit propagation, delegating to another Tracer implementation.

This library provides a utility-class to register a configured delegate tracer to become the implicit 'global' tracer instance available throughout the application.
If no Tracer is registered, the utility class will attempt to resolve it through the standard Java ContextLoader mechanism, looking for Tracer factory implementations that have declared themselves through an entry in the `"META-INF/services/io.opentracing.Tracer"` file.

The global tracer can be obtained by invoking the static `GlobalTracer.tracer()` method.
Even if no tracer was previously registered nor any tracer service factories were found, this static method will _always_ return a non-`null` Tracer implementation (opting for the no-op tracer by default).

The currently-active global Span can be obtained from a call to the static `GlobalSpanManager.activeSpan()` method.
Please note: this method may return `null` if no active span currently exists.

Starting a new span from the global tracer will automatically declare a 'child-of' reference to an active span if it exists.

The relation between these global tracer concepts and the Opentracing API are represented in the following UML diagram for this package:
<img src="http://plantuml.com/plantuml/png/hLR1RkCW4BttApXwbTOVK4LPRTDgrKCRQPiVmFQ4KDbW6PhTgjj_Ny1Ex036n5jVRC6R6PXvCyy88SHm1QB6EI3AKru3amtEAIDfpjKdpL9IyWoNwEFg2dLFNc8Xq4-plbLeQDGk6ZmVGc99yq-qQ81G8Q59MjotMtJWj1W1IuD95AX5ds2V_jWlDMPFc64IE03E9Js0Hcg_-Xq2OWc9nYYe4oBjL-wXG_Dcn5jU0cv-d68B7JpZNAqotwqeKpdQJHOXZJoKdGsS2UrGceDn0ObTXq22xzveMJW9Nc2ntgkDpSxaQwsE2N_agNBfmFBSJcMeaTvIM2uZVE6Y-0KxQ83bgcmgJunSeykEyvlA_Oi-qfQgDf2ugfEICn9fkSMaZslUCmmxMiomV7whCjDJKuOP9aInvL52PPYeFXgSbR1ymdNwe1ZslZo_nchbeyQHHXr7xg44eZf6XxNfCTnhotc-dxSLEXAXAzWvPaSP5Vl4DpEEUrsjt7q_pLi6WlJMpIMaXUmBDGazLSP5bfmaAksdX7zVZSBqxQvH93XmZzvN-B9tFKrYqaDy5XffFXplcrgHJbBEdfNYJ1tt67wJlK8kesur1lQEsLTymAcQjMlD4zqACqRcySlrjVoRfbypoVkpkvAtkBdvwY2ZEkHEc432Pch3k8iBRVzR5eRUvixva8_eaHzo4ZlzRH-pv6xen2_XVJzCKyVqX_EB4K53ZzISTUgqgWpX0O7nmCQqPdfKE0cCLQ20KK2C0bRZkZGqYfCeso9MhMoxkEl7Q9XrySlaR6XyWR9PCMD5pf-4aDw5aZ4kVICzQVh8TE7mHwrjm4ulzxG015Kjtz6EGba8UxK2kUU5-0S0">
