package com.chatssl.anytime.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.Attendance;
import com.chatssl.anytime.domain.AttendanceId;

public interface AttendanceRepository extends JpaRepository<Attendance, AttendanceId> {

    List<Attendance> findByRoomId(Long roomId);

    /** 16단계: user의 참석을 확인해 준 사람 수 (본인 확인 포함) */
    long countByRoomIdAndUserId(Long roomId, Long userId);

    /** 16단계: 평가를 마치면 (room, 나, 나) 행이 반드시 생긴다. 이걸로 '평가 완료'를 판단 */
    boolean existsByRoomIdAndConfirmerId(Long roomId, Long confirmerId);

    @Query("select distinct a.room.id from Attendance a where a.confirmer.id = :userId and a.room.id in :roomIds")
    List<Long> findReviewedRoomIds(@Param("userId") Long userId, @Param("roomIds") Collection<Long> roomIds);
}
