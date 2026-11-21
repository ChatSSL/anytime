package com.chatssl.anytime.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.Chat;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    Optional<Chat> findByRoomId(Long roomId);
}
