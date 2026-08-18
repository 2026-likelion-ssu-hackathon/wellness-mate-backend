package com.suspiciouslions.backend.domain.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.suspiciouslions.backend.domain.ai.entity.AiResultType;

public record AiAnalysisRequest(
		UUID analysisRequestId,
		Long chatRoomId,
		List<Participant> participants,
		List<AnalysisMessage> messages,
		List<RecentResult> recentResults
) {

	public record Participant(ParticipantKey participantKey) {
	}

	public record AnalysisMessage(
			Long messageId,
			ParticipantKey sender,
			String content,
			OffsetDateTime sentAt
	) {
	}

	public record RecentResult(
			AiResultType resultType,
			String referenceKey,
			OffsetDateTime createdAt
	) {
	}

	public enum ParticipantKey {
		USER_A,
		USER_B
	}
}
