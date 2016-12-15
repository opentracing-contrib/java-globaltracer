# java-globaltracer
Global Tracer, forwarding to another Tracer implementation.

This library provides two utility classes `ActiveSpanManager` and `GlobalTracer`:

## ActiveSpanManager
This class:
 1. Provides clients with the `ActiveSpanManger.activeSpan()` method to return the
    _active span_.
    If there is no active span, the method never returns a `null` refrence, but
    a `NoopSpan` instead.
 2. Modifications to the _active span_ can be made through the `activate` 
    and `deactivate` methods.
 3. Utility `spanAware()` methods to create 'span aware' Runnable and Callable instances
    that run with the _active span_ from the scheduling thread.

## GlobalTracer
This class has the following purpose:
 1. The `GlobalTracer.tracer()` factory method returning the singleton _global tracer_.
    If there is no global tracer, a `NoopTracer` is returned instead.
 2. Enrich the lifecycle of created `Span` objects through the global Tracer 
    to update the _active span_ as they are started and finished.
 3. Utility `traced()` methods to create Runnable and Callable instances 
    that run within a new Span that is _child of the active span_ form the scheduling thread.
    An `operationName` must be provided for a new Span to be created.

## How to use this library

  _TODO: rewrite the examples!_


## General overview
The relation between these concepts and the Opentracing API are represented in the following UML diagrams:  

_ActiveSpanManager:_  
<img src="http://plantuml.com/plantuml/png/hLNBRi8m4Bpp5PPwIKN91o14HUc50o2LglUDinXNZXt5XhP2_7ljF4XQygoQIvpLpAntlEiGGeY427KC50bNlefHcWGebyodoYvvu0Cr_EGWafn78v8_46W7D8GAq9eiKj3EWjOWWM5YiQJodBK1m-c5t6aqAsc_94NlXdK_lLMgrnd35UTPbdkt0S6Wvp1r2NotViOSfveIXdXLwfImQKwAXmrAL20afOPs1GHAsMXJ3SxznGSaw2r120W4pjxcUTbfl1Bkb7WvIkcmLNuH-qak3jaUMrbzQldV7AZePHu8qM73fUmuBWqc-rwDwdTkhzAUVLBiikvlA6RiTe2VfcZ4PVRP2DzpoVN1wx2eW0abMHUK2gMnKz2EtYimRsVTako1PE_QuMacvGSqaBKvHa4wtQqaePYtXdXMi1xdVHZEdmSodDUl_393RxkUW37RSok3qK2lMHtPaT95MyvjZ8aTT2KtzZeVK5gnlstmZjGCDQE9TxckRlyBaycyRhgcfBpGHotqyMJo7_EVfSX7_y1391Z5veliEOfG_uv6Q0ugrDy0">  
  
_GlobalTracer:_  
<img src="http://plantuml.com/plantuml/png/dLB1Ri8m3Btp5HPSCWqgxOeG4kkqdNOPq-vkOagcDAcIW3OX_dr9QMbXbA7b45dsUqwUdm40D1RaIkG4qYIc9EqjSgdpX9iGoYp9bSbGmMumWFfmXSx1IvL_3sYoeGgTitCUlUHxkBUKI-V9iWFkRWg7e0VkgoAx2IPOYafXfcZsCMyOefExJdxRQ1s1HsQJEDBshMAfWuRLXI5Tbe-PYm0VNiisKebbYPejmpHrVfIMnEHZh9xBxB2b_YJkMOIcmGxwycDuDhP0FqldOqX3jwsHefSV235YPvnEluxQ9ZenTZAPm_1XU5dt54fhjbAGQqVLl5vlISzI1O3SnhtJoQ8AuvTUKMD-RPgZd7mBPxMINhGxGSShC7uzLndnHDMFq9jUCqQYmHpw_Tde3tD6N17QXL9j8nSbPSQ4Whx5c7yCTmeRrzXLB_Ww60XKbFuRLfAKSEVPWlpQ2FS3">  

