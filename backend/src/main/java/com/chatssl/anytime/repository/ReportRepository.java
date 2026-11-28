package com.chatssl.anytime.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByTargetId(Long targetId);

    long countByTargetId(Long targetId);

    /** 같은 사람을 짧은 시간에 반복 신고했는지 (17단계) */
    boolean existsByReporterIdAndTargetIdAndCreatedAtAfter(Long reporterId, Long targetId, LocalDateTime after);
}
