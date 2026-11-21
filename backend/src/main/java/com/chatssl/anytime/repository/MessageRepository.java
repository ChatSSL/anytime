package com.chatssl.anytime.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 최신순 페이지 조회 (GET /chats/:id/messages) */
    Page<Message> findByChatIdOrderByIdDesc(Long chatId, Pageable pageable);

    /** 안 읽은 수: last_read_at 이후 메시지 수 */
    long countByChatIdAndCreatedAtAfter(Long chatId, LocalDateTime after);

    long countByChatId(Long chatId);
}
