package com.suspiciouslions.backend.domain.chat.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "메시지 전송 요청")
public record SendMessageRequest(
		@NotBlank
		@Schema(description = "클라이언트가 생성한 메시지 식별자", example = "client-message-uuid")
		String clientMessageId,
		@NotBlank
		@Schema(description = "메시지 내용. 공백만 있는 값은 허용하지 않음", example = "메시지 내용")
		String content,
		@NotNull
		@Schema(description = "클라이언트 전송 시각", example = "2026-08-18T16:00:00+09:00")
		OffsetDateTime sentAt
) {
}
