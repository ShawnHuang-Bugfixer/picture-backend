package com.xin.picturebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/27 14:16
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "cosCleanupExecutor")
    public Executor cosCleanupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);      // 常驻2线程（适合2核CPU）
        executor.setMaxPoolSize(3);       // 最大3线程（峰值时短暂扩容）
        executor.setQueueCapacity(5);    // 堆积10个任务
        executor.setThreadNamePrefix("cos-cleanup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 改为调用者处理
        executor.initialize();
        return executor;
    }

    @Bean(name = "messageEventExecutor")
    public Executor messageEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);  // 2核CPU建议核心线程数不超过2
        executor.setMaxPoolSize(3);   //
        executor.setQueueCapacity(1); //
        executor.setThreadNamePrefix("msg-event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); // 直接抛出异常
        executor.initialize();
        return executor;
    }

    @Bean(name = "cacheWarmupExecutor")
    public Executor cacheWarmupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0); // 关键：无队列，直接触发拒绝策略
        executor.setThreadNamePrefix("cache-warmup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}