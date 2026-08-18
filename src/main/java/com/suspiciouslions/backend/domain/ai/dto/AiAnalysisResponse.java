package com.suspiciouslions.backend.domain.ai.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest.ParticipantKey;
import com.suspiciouslions.backend.domain.ai.entity.AiResultType;
import com.suspiciouslions.backend.domain.ai.entity.ContentType;
import com.suspiciouslions.backend.domain.ai.entity.VisibilityType;
import com.suspiciouslions.backend.domain.emotion.entity.EmotionType;

public record AiAnalysisResponse(
		UUID analysisRequestId,
		AnalysisStatus status,
		List<Result> results,
		List<Emotion> emotionAnalyses,
		String errorCode,
		String errorMessage
) {

	public record Result(
			AiResultType resultType,
			VisibilityType visibilityType,
			ParticipantKey targetParticipant,
			ContentType contentType,
			List<Long> triggerMessageIds,
			Map<String, Object> resultData
	) {
	}

	public record Emotion(
			ParticipantKey subjectParticipant,
			ParticipantKey viewerParticipant,
			EmotionType emotionType,
			BigDecimal intensityValue,
			Boolean shouldShow,
			List<Long> triggerMessageIds,
			OffsetDateTime expiresAt,
			String stateText
	) {
	}

	public enum AnalysisStatus {
		COMPLETED,
		SKIPPED,
		FAILED
	}
}
