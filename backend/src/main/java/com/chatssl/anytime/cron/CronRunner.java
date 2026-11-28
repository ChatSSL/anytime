package com.chatssl.anytime.cron;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CronService 작업을 정해진 순서로 한 번 돌린다. 한 작업이 실패해도 나머지는 계속 돈다.
 * 1분 주기 실행(CronScheduler)과 로컬 수동 실행(DevCronController)이 같이 쓴다.
 * (각 작업의 트랜잭션이 걸리도록 CronService를 다른 빈에서 부른다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CronRunner {

    private final CronService cronService;

    public Map<String, Object> runAll() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("closeStarted", step("자동 취소·완료", cronService::closeStarted));
        out.put("rejectStaleChanges", step("일정 변경 정리", cronService::rejectStaleChanges));
        out.put("remind", step("리마인드", cronService::remind));
        out.put("applyRatings", step("평가 반영", cronService::applyRatings));
        return out;
    }

    /** 결과 중 하나라도 처리한 게 있거나 실패가 있으면 true (로그를 남길지 판단) */
    static boolean didSomething(Map<String, Object> result) {
        return result.values().stream().anyMatch(v -> {
            if (v instanceof Integer n) {
                return n > 0;
            }
            if (v instanceof CronService.CloseResult r) {
                return r.cancelled() > 0 || r.finished() > 0;
            }
            return v instanceof String; // 실패 메시지
        });
    }

    private Object step(String name, Supplier<?> job) {
        try {
            return job.get();
        } catch (RuntimeException e) {
            log.error("[cron] {} 실패", name, e);
            return "실패: " + e.getMessage();
        }
    }
}
