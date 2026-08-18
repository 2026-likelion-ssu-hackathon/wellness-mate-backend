package com.suspiciouslions.backend.domain.ai.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest.ParticipantKey;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse.Emotion;
import com.suspiciouslions.backend.domain.chat.entity.ChatRoom;
import com.suspiciouslions.backend.domain.chat.repository.ChatRoomRepository;
import com.suspiciouslions.backend.domain.emotion.entity.EmotionAnalysis;
import com.suspiciouslions.backend.domain.emotion.repository.EmotionAnalysisRepository;
import com.suspiciouslions.backend.domain.user.entity.User;

@Service
public class EmotionAnalysisPersistenceService {

	private static final BigDecimal MIN_INTENSITY = BigDecimal.ZERO;
	private static final BigDecimal MAX_INTENSITY = new BigDecimal("5.0");

	private final ChatRoomRepository chatRoomRepository;
	private final EmotionAnalysisRepository emotionAnalysisRepository;
	private final Clock clock;

	public EmotionAnalysisPersistenceService(ChatRoomRepository chatRoomRepository,
			EmotionAnalysisRepository emotionAnalysisRepository, Clock clock) {
		this.chatRoomRepository = chatRoomRepository;
		this.emotionAnalysisRepository = emotionAnalysisRepository;
		this.clock = clock;
	}

	@Transactional
	public void save(AiAnalysisRequest request, List<Emotion> emotions) {
		Objects.requireNonNull(emotions, "emotionAnalyses is required");
		ChatRoom chatRoom = findChatRoom(request.chatRoomId());
		Set<Long> requestedMessageIds = requestedMessageIds(request);
		OffsetDateTime detectedAt = OffsetDateTime.now(clock);

		List<EmotionAnalysis> entities = emotions.stream()
				.map(emotion -> toEntity(request, emotion, chatRoom, requestedMessageIds, detectedAt))
				.toList();
		emotionAnalysisRepository.saveAll(entities);
	}

	private EmotionAnalysis toEntity(AiAnalysisRequest request, Emotion emotion, ChatRoom chatRoom,
			Set<Long> requestedMessageIds, OffsetDateTime detectedAt) {
		Objects.requireNonNull(emotion.subjectParticipant(), "subjectParticipant is required");
		Objects.requireNonNull(emotion.viewerParticipant(), "viewerParticipant is required");
		Objects.requireNonNull(emotion.emotionType(), "emotionType is required");
		Objects.requireNonNull(emotion.intensityValue(), "intensityValue is required");
		Objects.requireNonNull(emotion.shouldShow(), "shouldShow is required");
		Objects.requireNonNull(emotion.expiresAt(), "expiresAt is required");

		if (emotion.subjectParticipant() == emotion.viewerParticipant()) {
			throw new IllegalArgumentException("subjectParticipant and viewerParticipant must be different");
		}
		if (emotion.intensityValue().compareTo(MIN_INTENSITY) < 0
				|| emotion.intensityValue().compareTo(MAX_INTENSITY) > 0) {
			throw new IllegalArgumentException("intensityValue must be between 0.0 and 5.0");
		}

		List<Long> triggerMessageIds = validatedTriggerMessageIds(
				emotion.triggerMessageIds(), requestedMessageIds);
		User subjectUser = participantUser(chatRoom, emotion.subjectParticipant());
		User viewerUser = participantUser(chatRoom, emotion.viewerParticipant());

		return new EmotionAnalysis(
				request.analysisRequestId(),
				chatRoom,
				subjectUser,
				viewerUser,
				emotion.emotionType(),
				emotion.intensityValue(),
				emotion.shouldShow(),
				triggerMessageIds,
				emotion.stateText(),
				detectedAt,
				emotion.expiresAt()
		);
	}

	private ChatRoom findChatRoom(Long chatRoomId) {
		return chatRoomRepository.findWithUsersById(chatRoomId)
				.orElseThrow(() -> new IllegalArgumentException("Chat room not found: " + chatRoomId));
	}

	private Set<Long> requestedMessageIds(AiAnalysisRequest request) {
		Objects.requireNonNull(request.messages(), "messages is required");
		Set<Long> messageIds = new LinkedHashSet<>();
		request.messages().forEach(message -> messageIds.add(message.messageId()));
		return messageIds;
	}

	private List<Long> validatedTriggerMessageIds(List<Long> triggerMessageIds, Set<Long> requestedMessageIds) {
		Objects.requireNonNull(triggerMessageIds, "triggerMessageIds is required");
		LinkedHashSet<Long> distinctIds = new LinkedHashSet<>(triggerMessageIds);
		if (!requestedMessageIds.containsAll(distinctIds)) {
			throw new IllegalArgumentException("triggerMessageIds contains a message not included in the request");
		}
		return List.copyOf(distinctIds);
	}

	private User participantUser(ChatRoom chatRoom, ParticipantKey participantKey) {
		return participantKey == ParticipantKey.USER_A ? chatRoom.getUserA() : chatRoom.getUserB();
	}
}
