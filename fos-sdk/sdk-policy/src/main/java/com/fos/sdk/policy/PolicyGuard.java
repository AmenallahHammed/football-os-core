package com.fos.sdk.policy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative ABAC guard. Apply to service methods that operate on owned resources.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PolicyGuard {
    /** The action string passed to the policy engine, e.g. "workspace.file.read" */
    String action();

    /** CanonicalType name of the resource being accessed, e.g. "TEAM", "PLAYER" */
    String resourceType() default "UNKNOWN";

    /**
     * Optional: name of the method parameter holding the resource ID.
     * Default: use the first UUID parameter in the method signature.
     */
    String resourceIdParam() default "";
}
