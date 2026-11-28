package com.chatssl.anytime.cron;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 로컬 시험용: 1분을 기다리지 않고 크론을 바로 한 번 돌린다.
 * dev-login처럼 app.auth.dev-login=true(local 프로필)일 때만 생기고, 서버(prod)에는 없다.
 */
@Tag(name = "개발용", description = "로컬에서만 보이는 시험용 API")
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.dev-login", havingValue = "true")
public class DevCronController {

    private final CronRunner cronRunner;

    @Operation(summary = "크론 지금 한 번 실행 (로컬 전용)",
        description = "자동 취소·완료 → 일정 변경 정리 → 리마인드 → 평가 반영을 바로 돌리고 처리한 개수를 돌려준다")
    @PostMapping("/api/dev/cron/run")
    public Map<String, Object> run() {
        return cronRunner.runAll();
    }
}
