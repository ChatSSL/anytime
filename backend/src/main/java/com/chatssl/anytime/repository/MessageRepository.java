package com.chatssl.anytime.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 최신순 페이지 조회 (GET /chats/:id/messages) */
    Page<Message> findByChatIdOrderByIdDesc(Long chatId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    @Query("select m from Message m where m.chat.id = :chatId order by m.id desc")
    Page<Message> findPageWithSenderByChatId(@Param("chatId") Long chatId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    @Query("""
        select m from Message m
        where m.id in (
            select max(m2.id) from Message m2
            where m2.chat.id in :chatIds
            group by m2.chat.id
        )
        """)
    List<Message> findLatestByChatIdIn(@Param("chatIds") Collection<Long> chatIds);

    /** 안 읽은 수: last_read_at 이후 메시지 수 */
    long countByChatIdAndCreatedAtAfter(Long chatId, LocalDateTime after);

    long countByChatId(Long chatId);
}
