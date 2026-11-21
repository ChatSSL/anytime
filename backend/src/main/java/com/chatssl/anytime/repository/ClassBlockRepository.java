package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.ClassBlock;

public interface ClassBlockRepository extends JpaRepository<ClassBlock, Long> {

    List<ClassBlock> findByUserIdOrderByDayAscStartMinAsc(Long userId);

    /** PUT /me/classes는 전체 교체라서 지우고 다시 넣는다. */
    void deleteByUserId(Long userId);
}
