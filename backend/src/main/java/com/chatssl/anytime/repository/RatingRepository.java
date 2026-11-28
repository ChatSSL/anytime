package com.chatssl.anytime.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.Rating;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    /** 크론이 모아서 반영할 평가 */
    List<Rating> findByAppliedAtIsNull();

    boolean existsByRoomIdAndRaterIdAndTargetId(Long roomId, Long raterId, Long targetId);

    boolean existsByRoomIdAndRaterId(Long roomId, Long raterId);

    /** 18단계 크론: cutoff 이전에 들어왔고 아직 반영 안 된 평가 (받은 사람을 같이 읽는다) */
    @Query("select r from Rating r join fetch r.target where r.appliedAt is null and r.createdAt <= :cutoff")
    List<Rating> findReadyToApply(@Param("cutoff") LocalDateTime cutoff);
}
