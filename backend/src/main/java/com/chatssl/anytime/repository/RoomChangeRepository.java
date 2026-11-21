package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.ChangeStatus;
import com.chatssl.anytime.domain.RoomChange;

public interface RoomChangeRepository extends JpaRepository<RoomChange, Long> {

    List<RoomChange> findByRoomIdAndStatus(Long roomId, ChangeStatus status);
}
