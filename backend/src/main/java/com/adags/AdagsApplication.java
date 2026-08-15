package com.adags;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AdagsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdagsApplication.class, args);
    }
}
