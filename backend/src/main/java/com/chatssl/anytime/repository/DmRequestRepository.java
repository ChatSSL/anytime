package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.DmRequest;
import com.chatssl.anytime.domain.DmStatus;

public interface DmRequestRepository extends JpaRepository<DmRequest, Long> {

    List<DmRequest> findByToUserIdAndStatus(Long toUserId, DmStatus status);

    List<DmRequest> findByFromUserId(Long fromUserId);
}
