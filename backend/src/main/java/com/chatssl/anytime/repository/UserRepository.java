package com.chatssl.anytime.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByNick(String nick);
}
