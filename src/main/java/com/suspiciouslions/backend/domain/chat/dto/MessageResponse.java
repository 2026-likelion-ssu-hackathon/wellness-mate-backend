package com.suspiciouslions.backend.domain.chat.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장된 채팅 메시지")
public record MessageResponse(
		@Schema(description = "메시지 ID", example = "101")
		Long messageId,
		@Schema(description = "채팅방 ID", example = "1")
		Long chatRoomId,
		@Schema(description = "발화자 사용자 ID", example = "1")
		Long senderId,
		@Schema(description = "클라이언트 메시지 식별자", example = "client-message-uuid")
		String clientMessageId,
		@Schema(description = "메시지 내용", example = "메시지 내용")
		String content,
		@Schema(description = "전송 시각", example = "2026-08-18T16:00:00+09:00")
		OffsetDateTime sentAt
) {
}
