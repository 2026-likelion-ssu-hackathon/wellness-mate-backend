package com.suspiciouslions.backend.domain.emotion.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.suspiciouslions.backend.domain.emotion.entity.EmotionType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 사용자에게 표시할 감정 상태")
public record EmotionAnalysisResponse(
		@Schema(description = "감정 분석 ID", example = "301")
		Long emotionAnalysisId,
		@Schema(description = "채팅방 ID", example = "1")
		Long chatRoomId,
		@Schema(description = "감정 상태의 대상 사용자 ID", example = "2")
		Long subjectUserId,
		@Schema(description = "감정 종류", example = "STABLE",
				allowableValues = {"STABLE", "RESOLVED", "ACCUMULATED", "ENGAGED", "ESCALATED"})
		EmotionType emotionType,
		@Schema(description = "감정 강도(0.0~5.0). STABLE의 0.0도 정상 상태입니다.", example = "0.0")
		BigDecimal intensityValue,
		@Schema(description = "화면에 표시할 상태 문구. 없을 수 있습니다.", example = "평온해요", nullable = true)
		String stateText,
		@Schema(description = "감정 감지 시각", example = "2026-08-18T16:00:00+09:00")
		OffsetDateTime detectedAt,
		@Schema(description = "감정 상태 만료 시각", example = "2026-08-18T16:40:00+09:00")
		OffsetDateTime expiresAt
) {
}
