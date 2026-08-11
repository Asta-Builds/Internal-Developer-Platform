package com.idp.config;

import com.idp.security.IdpPermissionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Wires the platform permission evaluator into method security.
 */
class MethodSecurityConfigTest {

    private final MethodSecurityConfig config = new MethodSecurityConfig();

    @Test
    @DisplayName("builds a handler that delegates to the platform evaluator")
    void buildsHandler() {
        IdpPermissionEvaluator evaluator = mock(IdpPermissionEvaluator.class);
        MethodSecurityExpressionHandler handler = config.methodSecurityExpressionHandler(evaluator);

        assertThat(handler).isInstanceOf(DefaultMethodSecurityExpressionHandler.class);
        Object evaluatorFromHandler =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(handler, "getPermissionEvaluator");
        assertThat(evaluatorFromHandler).isSameAs(evaluator);
    }
}
