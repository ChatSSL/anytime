package com.chatssl.anytime.cron;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 18단계: 1분마다 크론 작업을 돌린다. app.cron.enabled=false면 생기지 않는다 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.cron.enabled", havingValue = "true", matchIfMissing = true)
public class CronScheduler {

    private final CronRunner cronRunner;

    @Scheduled(fixedDelayString = "${app.cron.interval-ms:60000}", initialDelayString = "${app.cron.initial-delay-ms:10000}")
    public void tick() {
        Map<String, Object> result = cronRunner.runAll();
        if (CronRunner.didSomething(result)) {
            log.info("[cron] {}", result);
        }
    }
}
