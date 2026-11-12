package com.flashsale.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Injects the caller's user id into a controller method from the validated JWT subject:
 * {@code void buy(@AuthenticatedUser long userId)}. The id never comes from the request
 * path or body, so one user cannot act on behalf of another.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "T(java.lang.Long).parseLong(subject)")
public @interface AuthenticatedUser {
}
