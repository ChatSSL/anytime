package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.Rating;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    /** 크론이 모아서 반영할 평가 */
    List<Rating> findByAppliedAtIsNull();

    boolean existsByRoomIdAndRaterIdAndTargetId(Long roomId, Long raterId, Long targetId);

    boolean existsByRoomIdAndRaterId(Long roomId, Long raterId);
}
