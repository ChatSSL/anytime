package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.Attendance;
import com.chatssl.anytime.domain.AttendanceId;

public interface AttendanceRepository extends JpaRepository<Attendance, AttendanceId> {

    List<Attendance> findByRoomId(Long roomId);
}
