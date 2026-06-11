package org.sunyaxing.transflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransflowApplication.class, args);
    }
}
