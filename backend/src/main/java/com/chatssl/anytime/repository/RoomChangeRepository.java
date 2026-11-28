package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.ChangeStatus;
import com.chatssl.anytime.domain.RoomChange;

public interface RoomChangeRepository extends JpaRepository<RoomChange, Long> {

    List<RoomChange> findByRoomIdAndStatus(Long roomId, ChangeStatus status);

    /** 18단계 크론: 상태별 일정 변경 (방을 같이 읽는다) */
    @Query("select c from RoomChange c join fetch c.room where c.status = :status")
    List<RoomChange> findWithRoomByStatus(@Param("status") ChangeStatus status);
}
