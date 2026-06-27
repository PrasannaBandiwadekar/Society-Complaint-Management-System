package com.society.complaint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // powers the SLA-checking @Scheduled job
@EnableAsync        // powers the @Async email sending (so emails don't block requests)
public class ComplaintManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplaintManagementSystemApplication.class, args);
    }
}
