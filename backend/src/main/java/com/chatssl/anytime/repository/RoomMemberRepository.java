package com.chatssl.anytime.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.RoomMemberId;

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
}
