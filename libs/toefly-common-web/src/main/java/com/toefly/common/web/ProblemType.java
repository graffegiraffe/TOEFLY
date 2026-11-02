package com.toefly.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps an exception to a type from the error catalog (docs/05-api-contracts.md §3.2). The type URI is
 * {@code https://toefly.app/problems/<slug>} and the machine code is the slug in UPPER_SNAKE_CASE.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface ProblemType {

    /** Catalog slug, for example {@code version-mismatch}. */
    String value();

    /** HTTP status of the type. */
    int status() default 422;

    /** Stable English title of the type; derived from the slug when empty. */
    String title() default "";
}
