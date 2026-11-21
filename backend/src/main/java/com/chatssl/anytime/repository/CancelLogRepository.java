package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.CancelLog;

public interface CancelLogRepository extends JpaRepository<CancelLog, Long> {

    List<CancelLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}
