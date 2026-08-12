package com.idp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Executor for long-running platform pipelines (scaffolding runs).
 *
 * <p>Runs are bounded and isolated from the request threads: the caller gets
 * the 202 Accepted immediately and progress arrives over SSE.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "scaffoldExecutor")
    public Executor scaffoldExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("scaffold-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}