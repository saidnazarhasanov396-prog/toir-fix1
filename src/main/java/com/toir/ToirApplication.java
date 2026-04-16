package com.toir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ToirApplication {
    public static void main(String[] args) {
        SpringApplication.run(ToirApplication.class, args);
    }
}
