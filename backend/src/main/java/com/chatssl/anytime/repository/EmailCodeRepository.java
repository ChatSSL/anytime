package com.chatssl.anytime.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatssl.anytime.domain.EmailCode;

public interface EmailCodeRepository extends JpaRepository<EmailCode, String> {
}
