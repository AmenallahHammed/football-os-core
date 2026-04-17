package com.fos.sdk.security;

import java.lang.annotation.*;

/**
 * Marks a service method for automatic audit signal emission.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {
    String action();              // e.g. "workspace.file.read"
    String resourceType() default "";
}
