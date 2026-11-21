package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByTargetId(Long targetId);

    long countByTargetId(Long targetId);
}
