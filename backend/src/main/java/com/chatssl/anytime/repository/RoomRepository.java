package com.chatssl.anytime.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomStatus;

import jakarta.persistence.LockModeType;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByStatusOrderByDayAscStartMinAsc(RoomStatus status);

    List<Room> findByDayAndStatus(Integer day, RoomStatus status);

    /** from 날짜 이후(포함)에 모이는 방. 방장을 같이 읽는다 (모임 카드에 방장 닉네임) */
    @Query("""
        select r from Room r join fetch r.host
        where r.status = :status and r.meetDate >= :from
        """)
    List<Room> findUpcoming(@Param("status") RoomStatus status, @Param("from") LocalDate from);

    /** 주어진 시간대와 겹치는 모임. (day, start_min) 인덱스를 탄다. */
    @Query("""
        select r from Room r
        where r.status = :status and r.day = :day
          and r.startMin < :endMin and r.endMin > :startMin
        order by r.startMin
        """)
    List<Room> findOverlapping(@Param("status") RoomStatus status, @Param("day") int day,
                               @Param("startMin") int startMin, @Param("endMin") int endMin);

    @Query("select r from Room r join fetch r.host where r.id = :id")
    Optional<Room> findWithHostById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
