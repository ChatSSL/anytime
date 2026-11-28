package com.chatssl.anytime.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.domain.ChatMemberId;

public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {

    List<ChatMember> findByUserId(Long userId);

    List<ChatMember> findByChatId(Long chatId);

    boolean existsByChatIdAndUserId(Long chatId, Long userId);

    @Query("""
        select cm from ChatMember cm
        join fetch cm.chat c
        left join fetch c.room r
        where cm.user.id = :userId
        """)
    List<ChatMember> findByUserIdWithChat(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "user.interests"})
    @Query("select cm from ChatMember cm where cm.chat.id in :chatIds")
    List<ChatMember> findByChatIdInWithUser(@Param("chatIds") Collection<Long> chatIds);

    @EntityGraph(attributePaths = {"user", "user.interests"})
    @Query("select cm from ChatMember cm where cm.chat.id = :chatId")
    List<ChatMember> findByChatIdWithUser(@Param("chatId") Long chatId);

    @Query("""
        select a.chat.id from ChatMember a
        join ChatMember b on a.chat.id = b.chat.id
        where a.chat.kind = com.chatssl.anytime.domain.ChatKind.DM
          and a.user.id = :userA
          and b.user.id = :userB
        order by a.chat.id asc
        """)
    List<Long> findDmChatIdsBetween(@Param("userA") Long userA, @Param("userB") Long userB);
}
