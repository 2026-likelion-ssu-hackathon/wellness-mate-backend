package com.suspiciouslions.backend.domain.ai.event;

public record MessageCreatedEvent(Long messageId, Long chatRoomId) {
}
