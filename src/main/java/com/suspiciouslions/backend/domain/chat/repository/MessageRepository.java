package com.suspiciouslions.backend.domain.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suspiciouslions.backend.domain.chat.entity.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {
}
