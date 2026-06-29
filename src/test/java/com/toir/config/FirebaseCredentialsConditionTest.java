package com.toir.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseCredentialsConditionTest {

    @Test
    void isFalseWhenEnabledWithoutProjectIdOrCredentials() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.firebase.enabled", "true");

        assertThat(new FirebaseCredentialsCondition().matches(context(environment), null)).isFalse();
    }

    @Test
    void isTrueWhenEnabledWithProjectIdAndInlineJsonCredential() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.firebase.enabled", "true")
                .withProperty("app.firebase.project-id", "test-project")
                .withProperty("app.firebase.service-account-json", "{\"type\":\"service_account\"}");

        assertThat(new FirebaseCredentialsCondition().matches(context(environment), null)).isTrue();
    }

    private static ConditionContext context(Environment environment) {
        return new ConditionContext() {
            @Override
            public org.springframework.beans.factory.config.ConfigurableListableBeanFactory getBeanFactory() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Environment getEnvironment() {
                return environment;
            }

            @Override
            public ResourceLoader getResourceLoader() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ClassLoader getClassLoader() {
                return FirebaseCredentialsConditionTest.class.getClassLoader();
            }

            @Override
            public BeanDefinitionRegistry getRegistry() {
                return null;
            }
        };
    }
}
