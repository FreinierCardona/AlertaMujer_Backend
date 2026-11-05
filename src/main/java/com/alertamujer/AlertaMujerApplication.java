package com.alertamujer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlertaMujerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertaMujerApplication.class, args);
    }
}
