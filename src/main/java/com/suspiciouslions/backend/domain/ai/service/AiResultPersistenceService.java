package com.suspiciouslions.backend.domain.ai.service;

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
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse.Result;
import com.suspiciouslions.backend.domain.ai.entity.AiResult;
import com.suspiciouslions.backend.domain.ai.entity.VisibilityType;
import com.suspiciouslions.backend.domain.ai.repository.AiResultRepository;
import com.suspiciouslions.backend.domain.chat.entity.ChatRoom;
import com.suspiciouslions.backend.domain.chat.repository.ChatRoomRepository;
import com.suspiciouslions.backend.domain.user.entity.User;

@Service
public class AiResultPersistenceService {

	private final ChatRoomRepository chatRoomRepository;
	private final AiResultRepository aiResultRepository;
	private final Clock clock;

	public AiResultPersistenceService(ChatRoomRepository chatRoomRepository,
			AiResultRepository aiResultRepository, Clock clock) {
		this.chatRoomRepository = chatRoomRepository;
		this.aiResultRepository = aiResultRepository;
		this.clock = clock;
	}

	@Transactional
	public void save(AiAnalysisRequest request, List<Result> results) {
		Objects.requireNonNull(results, "results is required");
		ChatRoom chatRoom = findChatRoom(request.chatRoomId());
		Set<Long> requestedMessageIds = requestedMessageIds(request);
		OffsetDateTime createdAt = OffsetDateTime.now(clock);

		List<AiResult> entities = results.stream()
				.map(result -> toEntity(request, result, chatRoom, requestedMessageIds, createdAt))
				.toList();
		aiResultRepository.saveAll(entities);
	}

	private AiResult toEntity(AiAnalysisRequest request, Result result, ChatRoom chatRoom,
			Set<Long> requestedMessageIds, OffsetDateTime createdAt) {
		Objects.requireNonNull(result.resultType(), "resultType is required");
		Objects.requireNonNull(result.visibilityType(), "visibilityType is required");
		Objects.requireNonNull(result.contentType(), "contentType is required");
		Objects.requireNonNull(result.resultData(), "resultData is required");
		List<Long> triggerMessageIds = validatedTriggerMessageIds(result.triggerMessageIds(), requestedMessageIds);

		User recipientUser;
		if (result.visibilityType() == VisibilityType.INDIVIDUAL) {
			if (result.targetParticipant() == null) {
				throw new IllegalArgumentException("targetParticipant is required for INDIVIDUAL result");
			}
			recipientUser = participantUser(chatRoom, result.targetParticipant());
		} else {
			if (result.targetParticipant() != null) {
				throw new IllegalArgumentException("targetParticipant must be absent for COUPLE result");
			}
			recipientUser = null;
		}

		return new AiResult(
				request.analysisRequestId(),
				chatRoom,
				recipientUser,
				result.resultType(),
				result.visibilityType(),
				result.contentType(),
				triggerMessageIds,
				result.resultData(),
				createdAt
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
