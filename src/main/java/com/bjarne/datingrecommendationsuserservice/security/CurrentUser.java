package com.bjarne.datingrecommendationsuserservice.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

/**
 * Resolves the referenceId of the authenticated user (the JWT subject) into a String parameter.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "subject")
public @interface CurrentUser {
}
