package dev.errorlens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import dev.errorlens.infrastructure.observability.ContextPropagatingTaskDecorator;

/**
 * Executor limitado usado pelo TimeLimiter nas chamadas à dependência externa.
 * Propaga MDC, contexto de execução e tracing para as threads do pool.
 */
@Configuration
public class AsyncConfig {

    public static final String DEPENDENCY_EXECUTOR = "dependencyCallExecutor";

    @Bean(name = DEPENDENCY_EXECUTOR)
    ThreadPoolTaskExecutor dependencyCallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("el-dependency-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
