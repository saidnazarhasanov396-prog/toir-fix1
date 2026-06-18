package com.toir.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class FirebaseCredentialsCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return FirebaseDiagnostics.fromEnvironment(context.getEnvironment()).firebaseConfigurationReady();
    }
}
