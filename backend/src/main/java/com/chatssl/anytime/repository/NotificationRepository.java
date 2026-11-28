package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

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
}
