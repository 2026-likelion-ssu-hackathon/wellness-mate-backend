package com.suspiciouslions.backend.domain.ai;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.suspiciouslions.backend.domain.ai.client.AiWorkerClient;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest.ParticipantKey;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse.AnalysisStatus;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse.Emotion;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse.Result;
import com.suspiciouslions.backend.domain.ai.entity.AiResult;
import com.suspiciouslions.backend.domain.ai.entity.AiResultType;
import com.suspiciouslions.backend.domain.ai.entity.ContentType;
import com.suspiciouslions.backend.domain.ai.entity.VisibilityType;
import com.suspiciouslions.backend.domain.ai.repository.AiResultRepository;
import com.suspiciouslions.backend.domain.ai.service.AiAnalysisRequestService;
import com.suspiciouslions.backend.domain.ai.service.AiAnalysisResponseService;
import com.suspiciouslions.backend.domain.chat.dto.SendMessageRequest;
import com.suspiciouslions.backend.domain.chat.entity.ChatRoom;
import com.suspiciouslions.backend.domain.chat.entity.Message;
import com.suspiciouslions.backend.domain.chat.entity.RoomStatus;
import com.suspiciouslions.backend.domain.chat.repository.ChatRoomRepository;
import com.suspiciouslions.backend.domain.chat.repository.MessageRepository;
import com.suspiciouslions.backend.domain.chat.service.ChatService;
import com.suspiciouslions.backend.domain.emotion.entity.EmotionAnalysis;
import com.suspiciouslions.backend.domain.emotion.entity.EmotionType;
import com.suspiciouslions.backend.domain.emotion.repository.EmotionAnalysisRepository;
import com.suspiciouslions.backend.domain.user.entity.User;
import com.suspiciouslions.backend.domain.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@Import(AiWorkerIntegrationTests.TestConfig.class)
class AiWorkerIntegrationTests {

	private static final Instant FIXED_INSTANT = Instant.parse("2026-08-18T07:00:00Z");
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ChatRoomRepository chatRoomRepository;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private AiResultRepository aiResultRepository;

	@Autowired
	private EmotionAnalysisRepository emotionAnalysisRepository;

	@Autowired
	private AiAnalysisRequestService requestService;

	@Autowired
	private AiAnalysisResponseService responseService;

	@Autowired
	private ChatService chatService;

	@Autowired
	private FakeAiWorkerClient fakeAiWorkerClient;

	@BeforeEach
	void cleanDatabase() {
		fakeAiWorkerClient.reset();
		emotionAnalysisRepository.deleteAll();
		aiResultRepository.deleteAll();
		messageRepository.deleteAll();
		chatRoomRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void requestContainsLatestThirtyMessagesInChronologicalOrderAndMapsParticipants() {
		TestContext context = createTestContext();
		List<Message> messages = saveMessages(context, 32);

		AiAnalysisRequest request = requestService.create(context.chatRoom().getId());

		assertEquals(30, request.messages().size());
		assertEquals(messages.get(2).getId(), request.messages().get(0).messageId());
		assertEquals(messages.get(31).getId(), request.messages().get(29).messageId());
		assertEquals(ParticipantKey.USER_A, request.messages().get(0).sender());
		assertEquals(ParticipantKey.USER_B, request.messages().get(29).sender());
		assertEquals(List.of(ParticipantKey.USER_A, ParticipantKey.USER_B),
				request.participants().stream().map(AiAnalysisRequest.Participant::participantKey).toList());
		assertTrue(request.recentResults().isEmpty());
	}

	@Test
	void requestContainsAtMostTwentyRecommendationResultsFromRecentThirtyDays() {
		TestContext context = createTestContext();
		OffsetDateTime now = OffsetDateTime.now(FIXED_CLOCK);
		aiResultRepository.save(aiResult(
				context, AiResultType.DATE_RECOMMENDATION,
				Map.of("mainPlace", Map.of("name", "데이트 장소")), now.minusMinutes(1)));
		for (int index = 0; index < 21; index++) {
			aiResultRepository.save(aiResult(
					context, AiResultType.YOUTUBE_RECOMMENDATION,
					Map.of("videoId", "video-" + index), now.minusHours(index + 1L)));
		}
		aiResultRepository.save(aiResult(
				context, AiResultType.YOUTUBE_RECOMMENDATION,
				Map.of("videoId", "too-old"), now.minusDays(31)));
		aiResultRepository.save(aiResult(
				context, AiResultType.TONE_CORRECTION,
				Map.of("guideMessage", "제외"), now.minusMinutes(2)));

		AiAnalysisRequest request = requestService.create(context.chatRoom().getId());

		assertEquals(20, request.recentResults().size());
		assertEquals("데이트 장소", request.recentResults().get(0).referenceKey());
		assertFalse(request.recentResults().stream().anyMatch(result -> result.referenceKey().equals("too-old")));
		assertTrue(request.recentResults().stream()
				.allMatch(result -> result.resultType() != AiResultType.TONE_CORRECTION));
	}

	@Test
	void completedResponseSavesIndividualAndCoupleResults() {
		TestContext context = createTestContext();
		Message message = saveMessage(context, context.userA(), "trigger", 1);
		AiAnalysisRequest request = requestService.create(context.chatRoom().getId());
		List<Result> results = List.of(
				new Result(
						AiResultType.TONE_CORRECTION,
						VisibilityType.INDIVIDUAL,
						ParticipantKey.USER_B,
						ContentType.TEXT,
						List.of(message.getId(), message.getId()),
						Map.of("guideMessage", "대신 이렇게 말해보세요.")),
				new Result(
						AiResultType.YOUTUBE_RECOMMENDATION,
						VisibilityType.COUPLE,
						null,
						ContentType.MIXED,
						List.of(message.getId()),
						Map.of("videoId", "abc123"))
		);

		responseService.process(request, response(request, AnalysisStatus.COMPLETED, results, List.of()));

		List<AiResult> saved = aiResultRepository.findAll().stream()
				.sorted(Comparator.comparing(result -> result.getVisibilityType().name()))
				.toList();
		assertEquals(2, saved.size());
		AiResult couple = saved.stream()
				.filter(result -> result.getVisibilityType() == VisibilityType.COUPLE).findFirst().orElseThrow();
		AiResult individual = saved.stream()
				.filter(result -> result.getVisibilityType() == VisibilityType.INDIVIDUAL).findFirst().orElseThrow();
		assertNull(couple.getRecipientUser());
		assertEquals(context.userB().getId(), individual.getRecipientUser().getId());
		assertEquals(List.of(message.getId()), individual.getTriggerMessageIds());
		assertEquals("대신 이렇게 말해보세요.", individual.getResultData().get("guideMessage"));
	}

	@Test
	void emptyResultsStillSaveEmotionWithViewerMappingFalseFlagAndMissingStateText() {
		TestContext context = createTestContext();
		Message message = saveMessage(context, context.userA(), "trigger", 1);
		AiAnalysisRequest request = requestService.create(context.chatRoom().getId());
		Emotion emotion = new Emotion(
				ParticipantKey.USER_A,
				ParticipantKey.USER_B,
				EmotionType.ESCALATED,
				new BigDecimal("3.0"),
				false,
				List.of(message.getId(), message.getId()),
				now().plusHours(3),
				null
		);

		responseService.process(request,
				response(request, AnalysisStatus.COMPLETED, List.of(), List.of(emotion)));

		EmotionAnalysis saved = emotionAnalysisRepository.findAll().get(0);
		assertEquals(context.userA().getId(), saved.getSubjectUser().getId());
		assertEquals(context.userB().getId(), saved.getViewerUser().getId());
		assertFalse(saved.isShouldShow());
		assertNull(saved.getStateText());
		assertEquals(List.of(message.getId()), saved.getTriggerMessageIds());
	}

	@Test
	void skippedResponseStillSavesEmotion() {
		TestContext context = createTestContext();
		Message message = saveMessage(context, context.userB(), "trigger", 1);
		AiAnalysisRequest request = requestService.create(context.chatRoom().getId());
		Emotion emotion = new Emotion(
				ParticipantKey.USER_B,
				ParticipantKey.USER_A,
				EmotionType.STABLE,
				BigDecimal.ZERO,
				true,
				List.of(message.getId()),
				now().plusHours(3),
				"평온해요"
		);

		responseService.process(request,
				response(request, AnalysisStatus.SKIPPED, List.of(), List.of(emotion)));

		assertEquals(1, emotionAnalysisRepository.count());
		assertEquals("평온해요", emotionAnalysisRepository.findAll().get(0).getStateText());
	}

	@Test
	void failedResponseSavesNothing() {
		TestContext context = createTestContext();
		Message message = saveMessage(context, context.userA(), "trigger", 1);
		AiAnalysisRequest request = requestService.create(context.chatRoom().getId());
		Result result = new Result(
				AiResultType.TONE_CORRECTION, VisibilityType.INDIVIDUAL, ParticipantKey.USER_A,
				ContentType.TEXT, List.of(message.getId()), Map.of("guideMessage", "제외"));
		Emotion emotion = new Emotion(
				ParticipantKey.USER_A, ParticipantKey.USER_B, EmotionType.ESCALATED,
				BigDecimal.ONE, true, List.of(message.getId()), now().plusHours(3), "제외");

		responseService.process(request,
				new AiAnalysisResponse(request.analysisRequestId(), AnalysisStatus.FAILED,
						List.of(result), List.of(emotion), "MODEL_ERROR", "실패"));

		assertEquals(0, aiResultRepository.count());
		assertEquals(0, emotionAnalysisRepository.count());
	}

	@Test
	void mismatchedAnalysisRequestIdRejectsWholeResponse() {
		TestContext context = createTestContext();
		AiAnalysisRequest request = requestService.create(context.chatRoom().getId());
		AiAnalysisResponse mismatched = new AiAnalysisResponse(
				java.util.UUID.randomUUID(), AnalysisStatus.COMPLETED, List.of(), List.of(), null, null);

		assertThrows(IllegalArgumentException.class, () -> responseService.process(request, mismatched));
		assertEquals(0, aiResultRepository.count());
		assertEquals(0, emotionAnalysisRepository.count());
	}

	@Test
	void invalidResultTriggerIsRejectedWithoutBlockingValidEmotion() {
		TestContext context = createTestContext();
		Message message = saveMessage(context, context.userA(), "trigger", 1);
		AiAnalysisRequest request = requestService.create(context.chatRoom().getId());
		Result invalidResult = new Result(
				AiResultType.TONE_CORRECTION, VisibilityType.INDIVIDUAL, ParticipantKey.USER_A,
				ContentType.TEXT, List.of(Long.MAX_VALUE), Map.of("guideMessage", "제외"));
		Emotion validEmotion = new Emotion(
				ParticipantKey.USER_A, ParticipantKey.USER_B, EmotionType.ACCUMULATED,
				new BigDecimal("2.5"), true, List.of(message.getId()), now().plusHours(3), "서운해 보여요");

		responseService.process(request,
				response(request, AnalysisStatus.COMPLETED, List.of(invalidResult), List.of(validEmotion)));

		assertEquals(0, aiResultRepository.count());
		assertEquals(1, emotionAnalysisRepository.count());
	}

	@Test
	void invalidEmotionIntensityIsRejected() {
		TestContext context = createTestContext();
		Message message = saveMessage(context, context.userA(), "trigger", 1);
		AiAnalysisRequest request = requestService.create(context.chatRoom().getId());
		Emotion invalidEmotion = new Emotion(
				ParticipantKey.USER_A, ParticipantKey.USER_B, EmotionType.ESCALATED,
				new BigDecimal("5.1"), true, List.of(message.getId()), now().plusHours(3), "제외");

		responseService.process(request,
				response(request, AnalysisStatus.COMPLETED, List.of(), List.of(invalidEmotion)));

		assertEquals(0, emotionAnalysisRepository.count());
	}

	@Test
	void httpErrorDoesNotRollbackSavedMessage() throws InterruptedException {
		TestContext context = createTestContext();
		fakeAiWorkerClient.failWith(HttpServerErrorException.create(
				HttpStatus.INTERNAL_SERVER_ERROR, "worker error", HttpHeaders.EMPTY, new byte[0], null));

		chatService.sendMessage(context.chatRoom().getId(), context.userA().getId(),
				new SendMessageRequest("http-error", "저장되어야 함", now()));

		fakeAiWorkerClient.awaitFinished();
		assertEquals(1, messageRepository.count());
		assertEquals("저장되어야 함", messageRepository.findAll().get(0).getContent());
	}

	@Test
	void timeoutDoesNotRollbackSavedMessage() throws InterruptedException {
		TestContext context = createTestContext();
		fakeAiWorkerClient.failWith(new ResourceAccessException(
				"AI worker timeout", new SocketTimeoutException("timed out")));

		chatService.sendMessage(context.chatRoom().getId(), context.userB().getId(),
				new SendMessageRequest("timeout", "타임아웃이어도 저장", now()));

		fakeAiWorkerClient.awaitFinished();
		assertEquals(1, messageRepository.count());
		assertEquals("타임아웃이어도 저장", messageRepository.findAll().get(0).getContent());
	}

	private TestContext createTestContext() {
		User userA = saveUser("사용자 A");
		User userB = saveUser("사용자 B");
		ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(
				userA, userB, LocalDate.of(2026, 8, 1), RoomStatus.ACTIVE, now(), null));
		return new TestContext(userA, userB, chatRoom);
	}

	private User saveUser(String nickname) {
		return userRepository.save(new User(null, null, nickname, null, now(), now()));
	}

	private List<Message> saveMessages(TestContext context, int count) {
		List<Message> messages = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			User sender = index % 2 == 0 ? context.userA() : context.userB();
			messages.add(saveMessage(context, sender, "message-" + index, index));
		}
		return messages;
	}

	private Message saveMessage(TestContext context, User sender, String clientMessageId, int minute) {
		return messageRepository.save(new Message(
				context.chatRoom(), sender, clientMessageId, "메시지 " + minute, now().plusMinutes(minute)));
	}

	private AiResult aiResult(TestContext context, AiResultType resultType,
			Map<String, Object> resultData, OffsetDateTime createdAt) {
		return new AiResult(
				java.util.UUID.randomUUID(), context.chatRoom(), null, resultType, VisibilityType.COUPLE,
				ContentType.MIXED, List.of(), resultData, createdAt);
	}

	private AiAnalysisResponse response(AiAnalysisRequest request, AnalysisStatus status,
			List<Result> results, List<Emotion> emotions) {
		return new AiAnalysisResponse(request.analysisRequestId(), status, results, emotions, null, null);
	}

	private OffsetDateTime now() {
		return OffsetDateTime.now(FIXED_CLOCK);
	}

	private record TestContext(User userA, User userB, ChatRoom chatRoom) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		@Primary
		Clock fixedClock() {
			return FIXED_CLOCK;
		}

		@Bean
		@Primary
		FakeAiWorkerClient fakeAiWorkerClient() {
			return new FakeAiWorkerClient();
		}
	}

	static class FakeAiWorkerClient implements AiWorkerClient {

		private volatile RuntimeException failure;
		private volatile CountDownLatch finished = new CountDownLatch(1);

		void reset() {
			failure = null;
			finished = new CountDownLatch(1);
		}

		void failWith(RuntimeException exception) {
			failure = exception;
		}

		void awaitFinished() throws InterruptedException {
			assertTrue(finished.await(5, TimeUnit.SECONDS), "AI analysis did not finish in time");
		}

		@Override
		public AiAnalysisResponse analyze(AiAnalysisRequest request) {
			try {
				if (failure != null) {
					throw failure;
				}
				return new AiAnalysisResponse(
						request.analysisRequestId(), AnalysisStatus.SKIPPED, List.of(), List.of(), null, null);
			} finally {
				finished.countDown();
			}
		}
	}
}
