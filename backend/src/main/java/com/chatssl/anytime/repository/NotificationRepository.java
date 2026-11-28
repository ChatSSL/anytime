package com.chatssl.anytime.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    /** 알림 센터 목록 (17단계). 최근 100개까지 */
    List<Notification> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    /** 내 알림인지까지 같이 확인한다 (남의 알림 id로 읽음 처리 방지) */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** 모두 읽음 (17단계). 바뀐 개수를 돌려준다 */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("""
        select n from Notification n
        where n.user.id = :userId
          and n.relatedUser.id = :relatedUserId
          and n.type = :type
          and n.needsAction = true
          and n.resolved is null
        """)
    List<Notification> findPendingActionNotifications(
        @Param("userId") Long userId,
        @Param("relatedUserId") Long relatedUserId,
        @Param("type") NotifType type
    );

    /** 15단계: 일정 변경처럼 link로 묶이는 '답변 필요' 알림 중 아직 처리 안 한 것 */
    List<Notification> findByUserIdAndTypeAndLinkAndNeedsActionTrueAndResolvedIsNull(
        Long userId, NotifType type, String link);
}
