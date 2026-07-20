package com.society.complaint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@SpringBootApplication
@EnableScheduling   // SLA-checking @Scheduled job
@EnableAsync        // kept for any remaining @Async tasks
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
// ^^^^ Stores HTTP sessions in Redis instead of in app memory.
//      maxInactiveIntervalInSeconds = 3600 means sessions expire after 1 hour of inactivity.
//      Benefit 1: users stay logged in even if the app container restarts.
//      Benefit 2: if you deploy two instances of the app later, both share the same sessions.
public class ComplaintManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplaintManagementSystemApplication.class, args);
    }
}
