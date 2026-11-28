package com.chatssl.anytime.notification;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.common.CurrentUserId;
import com.chatssl.anytime.notification.dto.NotificationListResponse;
import com.chatssl.anytime.notification.dto.NotificationResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "알림", description = "알림 센터 목록·읽음 처리 (17단계)")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationInboxService inboxService;

    @Operation(summary = "내 알림 목록", description = "최근 100개, 최신순. 차단 관계인 사람이 관련된 알림은 빠진다")
    @GetMapping
    public NotificationListResponse list(@CurrentUserId Long userId) {
        return inboxService.list(userId);
    }

    @Operation(summary = "알림 하나 읽음", description = "내 알림이 아니면 404 NOTIFICATION_NOT_FOUND")
    @PostMapping("/{id}/read")
    public NotificationResponse read(@CurrentUserId Long userId, @PathVariable Long id) {
        return inboxService.read(userId, id);
    }

    @Operation(summary = "알림 모두 읽음", description = "{\"updated\": 바뀐 개수}")
    @PostMapping("/read-all")
    public Map<String, Integer> readAll(@CurrentUserId Long userId) {
        return Map.of("updated", inboxService.readAll(userId));
    }
}
