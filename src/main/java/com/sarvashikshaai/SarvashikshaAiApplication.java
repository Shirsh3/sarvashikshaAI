package com.sarvashikshaai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SarvashikshaAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SarvashikshaAiApplication.class, args);
    }
}

