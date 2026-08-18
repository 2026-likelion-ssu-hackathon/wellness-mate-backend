package com.suspiciouslions.backend.domain.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suspiciouslions.backend.domain.chat.entity.ChatRoom;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
}
