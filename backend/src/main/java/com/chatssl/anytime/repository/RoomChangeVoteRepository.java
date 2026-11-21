package com.chatssl.anytime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.RoomChangeVote;
import com.chatssl.anytime.domain.RoomChangeVoteId;

public interface RoomChangeVoteRepository extends JpaRepository<RoomChangeVote, RoomChangeVoteId> {

    List<RoomChangeVote> findByChangeId(Long changeId);
}
