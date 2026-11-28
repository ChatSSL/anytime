package com.chatssl.anytime.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.Chat;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    Optional<Chat> findByRoomId(Long roomId);

    @Query("select c from Chat c left join fetch c.room where c.id = :id")
    Optional<Chat> findWithRoomById(@Param("id") Long id);
}
