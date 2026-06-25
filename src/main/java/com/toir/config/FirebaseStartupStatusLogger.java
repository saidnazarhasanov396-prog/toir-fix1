package com.toir.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseStartupStatusLogger implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        log.info("Firebase Cloud Messaging is configured from classpath:firebase-service-account.json");
    }
}
