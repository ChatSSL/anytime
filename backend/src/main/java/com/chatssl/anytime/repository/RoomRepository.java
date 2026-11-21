package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomStatus;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByStatusOrderByDayAscStartMinAsc(RoomStatus status);

    List<Room> findByDayAndStatus(Integer day, RoomStatus status);

    /** 주어진 시간대와 겹치는 모임. (day, start_min) 인덱스를 탄다. */
    @Query("""
        select r from Room r
        where r.status = :status and r.day = :day
          and r.startMin < :endMin and r.endMin > :startMin
        order by r.startMin
        """)
    List<Room> findOverlapping(@Param("status") RoomStatus status, @Param("day") int day,
                               @Param("startMin") int startMin, @Param("endMin") int endMin);
}
