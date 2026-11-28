package com.chatssl.anytime.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.Block;
import com.chatssl.anytime.domain.BlockId;

public interface BlockRepository extends JpaRepository<Block, BlockId> {

    /**
     * 차단 양방향 필터. 내가 차단했거나 나를 차단한 사람의 id.
     * 추천·채팅·모임 목록에서 이 id들을 빼고 보여준다.
     */
    @Query("""
        select case when b.blocker.id = :userId then b.blocked.id else b.blocker.id end
        from Block b
        where b.blocker.id = :userId or b.blocked.id = :userId
        """)
    Set<Long> findHiddenUserIds(@Param("userId") Long userId);

    /** 내가 차단한 사람 목록 (17단계 차단 관리 화면). 차단당한 사람을 같이 읽는다 */
    @Query("""
        select b from Block b join fetch b.blocked
        where b.blocker.id = :userId
        order by b.createdAt desc
        """)
    List<Block> findByBlockerWithUser(@Param("userId") Long userId);
}
