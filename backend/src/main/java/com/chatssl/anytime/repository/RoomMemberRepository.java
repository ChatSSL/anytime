package com.chatssl.anytime.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.RoomMemberId;
import com.chatssl.anytime.domain.RoomStatus;

public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {

    /** 현재 참여 중인 사람 (나간 사람 제외) */
    List<RoomMember> findByRoomIdAndLeftAtIsNull(Long roomId);

    long countByRoomIdAndLeftAtIsNull(Long roomId);

    /** 여러 방의 현재 참여자를 한 번에. getId().getRoomId()/getUserId()만 쓰면 방·사용자를 따로 읽지 않는다 */
    List<RoomMember> findByRoomIdInAndLeftAtIsNull(Collection<Long> roomIds);

    List<RoomMember> findByUserIdAndLeftAtIsNull(Long userId);

    @Query("select case when count(m) > 0 then true else false end from RoomMember m "
         + "where m.room.id = :roomId and m.user.id = :userId and m.leftAt is null")
    boolean isActiveMember(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "user.interests"})
    @Query("""
        select m from RoomMember m
        where m.room.id = :roomId and m.leftAt is null
        order by m.joinedAt asc, m.user.id asc
        """)
    List<RoomMember> findActiveWithUserByRoomId(@Param("roomId") Long roomId);

    @Query("""
        select m from RoomMember m join fetch m.room r
        where m.user.id = :userId
          and m.leftAt is null
          and r.status = :status
          and r.meetDate = :meetDate
          and r.startMin < :endMin
          and r.endMin > :startMin
        """)
    List<RoomMember> findActiveOverlapping(
        @Param("userId") Long userId,
        @Param("status") RoomStatus status,
        @Param("meetDate") LocalDate meetDate,
        @Param("startMin") int startMin,
        @Param("endMin") int endMin
    );

    /** 16단계: 평가할 수 있는 내 모임 후보 (from~to 사이에 모였고 취소되지 않은 방). 방·방장을 같이 읽는다 */
    @Query("""
        select m from RoomMember m join fetch m.room r join fetch r.host
        where m.user.id = :userId
          and m.leftAt is null
          and r.status <> :excluded
          and r.meetDate between :from and :to
        """)
    List<RoomMember> findMineBetween(
        @Param("userId") Long userId,
        @Param("excluded") RoomStatus excluded,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
