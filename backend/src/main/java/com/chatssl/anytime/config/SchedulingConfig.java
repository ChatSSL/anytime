package com.chatssl.anytime.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 18단계: @Scheduled(크론)를 켠다. 실제 작업은 cron.CronScheduler */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
