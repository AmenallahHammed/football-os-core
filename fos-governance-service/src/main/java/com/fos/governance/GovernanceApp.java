package com.fos.governance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.data.jpa.repository.config.EnableJpaAuditing
public class GovernanceApp {
    public static void main(String[] args) {
        SpringApplication.run(GovernanceApp.class, args);
    }
}
