package com.suspiciouslions.backend.domain.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.suspiciouslions.backend.domain.ai.entity.AiResultType;
import com.suspiciouslions.backend.domain.ai.entity.ContentType;
import com.suspiciouslions.backend.domain.ai.entity.VisibilityType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장된 AI 분석 결과")
public record AiResultResponse(
		@Schema(description = "AI 결과 ID", example = "201")
		Long resultId,
		@Schema(description = "채팅방 ID", example = "1")
		Long chatRoomId,
		@Schema(description = "AI 결과 종류", example = "DATE_RECOMMENDATION")
		AiResultType resultType,
		@Schema(description = "결과 노출 범위", example = "COUPLE")
		VisibilityType visibilityType,
		@Schema(description = "결과 표현 형식", example = "MIXED")
		ContentType contentType,
		@Schema(description = "결과를 발생시킨 메시지 ID 목록", example = "[101, 102]")
		List<Long> triggerMessageIds,
		@Schema(description = "저장된 중첩 JSON 결과 데이터")
		Map<String, Object> resultData,
		@Schema(description = "결과 저장 시각", example = "2026-08-18T16:00:00+09:00")
		OffsetDateTime createdAt
) {
}
