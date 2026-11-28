package com.chatssl.anytime.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.DmRequest;
import com.chatssl.anytime.domain.DmStatus;

import jakarta.persistence.LockModeType;

public interface DmRequestRepository extends JpaRepository<DmRequest, Long> {

    List<DmRequest> findByToUserIdAndStatus(Long toUserId, DmStatus status);

    List<DmRequest> findByFromUserId(Long fromUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DmRequest d where d.id = :id")
    Optional<DmRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        select case when count(d) > 0 then true else false end
        from DmRequest d
        where d.status = com.chatssl.anytime.domain.DmStatus.PENDING
          and ((d.fromUser.id = :userA and d.toUser.id = :userB)
            or (d.fromUser.id = :userB and d.toUser.id = :userA))
        """)
    boolean existsPendingBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    @EntityGraph(attributePaths = {"fromUser", "fromUser.interests", "toUser", "toUser.interests", "chat"})
    @Query("""
        select d from DmRequest d
        where d.toUser.id = :toUserId and d.status = :status
        order by d.createdAt desc, d.id desc
        """)
    List<DmRequest> findReceivedWithUsers(@Param("toUserId") Long toUserId, @Param("status") DmStatus status);

    @EntityGraph(attributePaths = {"fromUser", "fromUser.interests", "toUser", "toUser.interests", "chat"})
    @Query("""
        select d from DmRequest d
        where d.fromUser.id = :fromUserId
        order by d.createdAt desc, d.id desc
        """)
    List<DmRequest> findSentWithUsers(@Param("fromUserId") Long fromUserId);
}
