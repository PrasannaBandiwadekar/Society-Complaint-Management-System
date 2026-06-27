package com.society.complaint.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.lang.reflect.Method;

@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
                log.error("================ ASYNC TASK FAILED ================");
                log.error("Method: {}", method.getName());
                log.error("Exception: {}", throwable.getMessage(), throwable);
                log.error("=====================================================");
            }
        };
    }
}