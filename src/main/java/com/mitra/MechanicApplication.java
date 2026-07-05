package com.mitra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MechanicApplication {

    public static void main(String[] args) {
        SpringApplication.run(MechanicApplication.class, args);
    }
}
