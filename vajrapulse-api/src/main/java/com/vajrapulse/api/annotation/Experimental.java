package com.vajrapulse.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API element as experimental — subject to incompatible changes or removal
 * in future releases. Experimental APIs are functional but not yet finalized.
 * Users must explicitly opt in by acknowledging the experimental status.
 *
 * <p>Types annotated with {@code @Experimental} ship with known caveats and should
 * not be relied upon in production until the annotation is removed in a stable release.
 *
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PACKAGE})
public @interface Experimental {
    /**
     * The release where this API is expected to stabilize.
     * Empty string means no target release has been set.
     */
    String expectedStable() default "";

    /** Reason this API is marked experimental. */
    String reason() default "";
}
