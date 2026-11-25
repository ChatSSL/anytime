package com.chatssl.anytime.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatssl.anytime.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByNick(String nick);

    /**
     * 사람 추천 후보 (10단계): 나를 빼고, 제재가 sanctionBelow 미만이고, 만나고 싶은 공강이 하나라도 있는 사람.
     * 관심사와 공강을 한 번에 같이 읽는다(사람마다 따로 조회하지 않게).
     */
    @EntityGraph(attributePaths = {"interests", "wantedSlots"})
    @Query("""
        select u from User u
        where u.id <> :userId and u.sanction < :sanctionBelow and size(u.wantedSlots) > 0
        """)
    List<User> findRecommendCandidates(@Param("userId") Long userId, @Param("sanctionBelow") int sanctionBelow);
}
