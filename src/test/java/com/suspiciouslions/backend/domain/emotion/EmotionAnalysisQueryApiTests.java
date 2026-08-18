package com.suspiciouslions.backend.domain.emotion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.suspiciouslions.backend.domain.ai.client.AiWorkerClient;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse.AnalysisStatus;
import com.suspiciouslions.backend.domain.chat.entity.ChatRoom;
import com.suspiciouslions.backend.domain.chat.entity.RoomStatus;
import com.suspiciouslions.backend.domain.chat.repository.ChatRoomRepository;
import com.suspiciouslions.backend.domain.emotion.dto.EmotionAnalysisResponse;
import com.suspiciouslions.backend.domain.emotion.entity.EmotionAnalysis;
import com.suspiciouslions.backend.domain.emotion.entity.EmotionType;
import com.suspiciouslions.backend.domain.emotion.repository.EmotionAnalysisRepository;
import com.suspiciouslions.backend.domain.emotion.service.EmotionAnalysisQueryService;
import com.suspiciouslions.backend.domain.user.entity.User;
import com.suspiciouslions.backend.domain.user.repository.UserRepository;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(EmotionAnalysisQueryApiTests.TestConfig.class)
class EmotionAnalysisQueryApiTests {

	private static final Instant FIXED_INSTANT = Instant.parse("2026-08-18T07:00:00Z");
	private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ChatRoomRepository chatRoomRepository;

	@Autowired
	private EmotionAnalysisRepository emotionAnalysisRepository;

	@Autowired
	private EmotionAnalysisQueryService emotionAnalysisQueryService;

	@Autowired
	private CountingAiWorkerClient aiWorkerClient;

	@BeforeEach
	void resetClientCount() {
		aiWorkerClient.reset();
	}

	@AfterEach
	void queryNeverCallsAiWorker() {
		assertEquals(0, aiWorkerClient.callCount());
	}

	@Test
	void participantGetsOnlyStatesAddressedToThemInRequestedRoom() throws Exception {
		TestContext context = createTestContext();
		EmotionAnalysis visible = saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.ACCUMULATED, "2.5", true, "서운해 보여요", -5, 30);
		saveEmotion(context.chatRoom(), context.userA(), context.userB(),
				EmotionType.ENGAGED, "1.5", true, "대화에 집중해요", -4, 30);
		saveEmotion(context.otherChatRoom(), context.outsider(), context.otherPartner(),
				EmotionType.ESCALATED, "4.0", true, "감정이 높아요", -3, 30);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/emotion-analyses", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].emotionAnalysisId").value(visible.getId()))
				.andExpect(jsonPath("$[0].chatRoomId").value(context.chatRoom().getId()))
				.andExpect(jsonPath("$[0].subjectUserId").value(context.userB().getId()));
	}

	@Test
	void repositoryReturnsLatestShouldShowTrueUsingDetectedAtThenId() {
		TestContext context = createTestContext();
		saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.RESOLVED, "1.0", true, "이전", -10, 30);
		saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.ACCUMULATED, "2.0", true, "같은 시각의 이전 ID", -5, 30);
		EmotionAnalysis latestId = saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.ESCALATED, "3.0", true, "같은 시각의 최신 ID", -5, 30);

		List<EmotionAnalysis> states = emotionAnalysisRepository.findCurrentVisibleStates(
				context.chatRoom().getId(), context.userA().getId(), now());

		assertEquals(List.of(latestId.getId()), states.stream().map(EmotionAnalysis::getId).toList());
	}

	@Test
	void latestShouldShowFalseDoesNotReplacePreviousVisibleState() throws Exception {
		TestContext context = createTestContext();
		EmotionAnalysis previousVisible = saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.RESOLVED, "1.0", true, "회복 중이에요", -10, 30);
		saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.ESCALATED, "4.0", false, "갱신하지 않음", -1, 30);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/emotion-analyses", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].emotionAnalysisId", contains(previousVisible.getId().intValue())))
				.andExpect(jsonPath("$[0].emotionType").value("RESOLVED"));
	}

	@Test
	void expiredLatestStateIsExcludedAndEarlierUnexpiredStateIsMaintained() {
		TestContext context = createTestContext();
		EmotionAnalysis earlierUnexpired = saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.ENGAGED, "1.5", true, "유효한 이전 상태", -10, 20);
		saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.ESCALATED, "4.0", true, "만료된 최신 상태", -2, -1);

		List<EmotionAnalysisResponse> states = emotionAnalysisQueryService.getCurrentStates(
				context.chatRoom().getId(), context.userA().getId());

		assertEquals(List.of(earlierUnexpired.getId()),
				states.stream().map(EmotionAnalysisResponse::emotionAnalysisId).toList());
	}

	@Test
	void allPreviousVisibleStatesExpiredReturnsEmptyArray() throws Exception {
		TestContext context = createTestContext();
		saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.ACCUMULATED, "2.0", true, "이미 만료", -20, -10);
		saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.ESCALATED, "4.0", false, "표시하지 않음", -1, 20);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/emotion-analyses", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void stableWithZeroIntensityIsReturnedNormally() throws Exception {
		TestContext context = createTestContext();
		saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.STABLE, "0.0", true, "평온해요", -1, 40);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/emotion-analyses", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].emotionType").value("STABLE"))
				.andExpect(jsonPath("$[0].intensityValue").value(0.0))
				.andExpect(jsonPath("$[0].stateText").value("평온해요"));
	}

	@Test
	void nullableStateTextIsReturnedAsNull() {
		TestContext context = createTestContext();
		saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.STABLE, "0.0", true, null, -1, 40);

		EmotionAnalysisResponse response = emotionAnalysisQueryService.getCurrentStates(
				context.chatRoom().getId(), context.userA().getId()).get(0);

		assertNull(response.stateText());
	}

	@Test
	void multipleSubjectsAreReturnedInSubjectIdOrder() {
		TestContext context = createTestContext();
		EmotionAnalysis firstSubject = saveEmotion(context.chatRoom(), context.userB(), context.userA(),
				EmotionType.STABLE, "0.0", true, "첫 번째", -1, 30);
		EmotionAnalysis secondSubject = saveEmotion(context.chatRoom(), context.outsider(), context.userA(),
				EmotionType.ENGAGED, "1.0", true, "두 번째", -2, 30);

		List<EmotionAnalysis> states = emotionAnalysisRepository.findCurrentVisibleStates(
				context.chatRoom().getId(), context.userA().getId(), now());

		assertEquals(List.of(firstSubject.getId(), secondSubject.getId()),
				states.stream().map(EmotionAnalysis::getId).toList());
	}

	@Test
	void noStoredStatesReturnsOkWithEmptyArray() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/emotion-analyses", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void unknownUserReturnsNotFound() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/emotion-analyses", context.chatRoom().getId())
					.header("X-User-Id", Long.MAX_VALUE))
				.andExpect(status().isNotFound());
	}

	@Test
	void unknownChatRoomReturnsNotFound() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/emotion-analyses", Long.MAX_VALUE)
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	void nonParticipantReturnsForbidden() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/emotion-analyses", context.chatRoom().getId())
					.header("X-User-Id", context.outsider().getId()))
				.andExpect(status().isForbidden());
	}

	@Test
	void nonPositivePathAndHeaderReturnBadRequest() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/emotion-analyses", 0)
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/emotion-analyses", context.chatRoom().getId())
					.header("X-User-Id", 0))
				.andExpect(status().isBadRequest());
	}

	@Test
	void serviceUsesSameNotFoundAndForbiddenRules() {
		TestContext context = createTestContext();

		ResponseStatusException unknownUser = assertThrows(ResponseStatusException.class,
				() -> emotionAnalysisQueryService.getCurrentStates(context.chatRoom().getId(), Long.MAX_VALUE));
		ResponseStatusException unknownRoom = assertThrows(ResponseStatusException.class,
				() -> emotionAnalysisQueryService.getCurrentStates(Long.MAX_VALUE, context.userA().getId()));
		ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
				() -> emotionAnalysisQueryService.getCurrentStates(
						context.chatRoom().getId(), context.outsider().getId()));

		assertEquals(404, unknownUser.getStatusCode().value());
		assertEquals(404, unknownRoom.getStatusCode().value());
		assertEquals(403, forbidden.getStatusCode().value());
	}

	@Test
	void swaggerDocumentsPathHeaderResponseEnumExampleAndErrors() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get.summary")
						.value("감정 상태 조회"))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get.tags[0]")
						.value("Emotion Analyses"))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get.parameters[*].name",
						hasItems("chatRoomId", "X-User-Id")))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get.parameters[0].description")
						.value("감정 상태를 조회할 채팅방 ID"))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get.parameters[1].description")
						.value("감정 상태를 보는 현재 임시 사용자 ID"))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get.responses['403']")
						.exists())
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get.responses['404']")
						.exists())
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get"
						+ ".responses['200'].content['application/json'].schema.type")
						.value("array"))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get"
						+ ".responses['200'].content['application/json'].schema.items['$ref']")
						.value("#/components/schemas/EmotionAnalysisResponse"))
				.andExpect(jsonPath("$.components.schemas.EmotionAnalysisResponse.properties.emotionType.enum",
						contains("STABLE", "RESOLVED", "ACCUMULATED", "ENGAGED", "ESCALATED")))
				.andExpect(jsonPath("$.components.schemas.EmotionAnalysisResponse.properties.intensityValue.example")
						.value(0.0))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get"
						+ ".responses['200'].content['application/json'].example[0].emotionType")
						.value("STABLE"))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/emotion-analyses'].get"
						+ ".responses['200'].content['application/json'].example[0].intensityValue")
						.value(0.0));
	}

	private TestContext createTestContext() {
		User userA = saveUser("사용자 A");
		User userB = saveUser("사용자 B");
		User outsider = saveUser("외부 사용자");
		User otherPartner = saveUser("다른 상대방");
		ChatRoom chatRoom = saveChatRoom(userA, userB);
		ChatRoom otherChatRoom = saveChatRoom(outsider, otherPartner);
		return new TestContext(userA, userB, outsider, otherPartner, chatRoom, otherChatRoom);
	}

	private User saveUser(String nickname) {
		return userRepository.save(new User(null, null, nickname, null, now(), now()));
	}

	private ChatRoom saveChatRoom(User userA, User userB) {
		return chatRoomRepository.save(new ChatRoom(
				userA, userB, LocalDate.of(2026, 8, 1), RoomStatus.ACTIVE, now(), null));
	}

	private EmotionAnalysis saveEmotion(ChatRoom chatRoom, User subject, User viewer,
			EmotionType emotionType, String intensity, boolean shouldShow, String stateText,
			int detectedMinute, int expiresMinute) {
		return emotionAnalysisRepository.save(new EmotionAnalysis(
				UUID.randomUUID(),
				chatRoom,
				subject,
				viewer,
				emotionType,
				new BigDecimal(intensity),
				shouldShow,
				List.of(),
				stateText,
				now().plusMinutes(detectedMinute),
				now().plusMinutes(expiresMinute)
		));
	}

	private OffsetDateTime now() {
		return OffsetDateTime.now(FIXED_CLOCK);
	}

	private record TestContext(
			User userA,
			User userB,
			User outsider,
			User otherPartner,
			ChatRoom chatRoom,
			ChatRoom otherChatRoom
	) {
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
		CountingAiWorkerClient countingAiWorkerClient() {
			return new CountingAiWorkerClient();
		}
	}

	static class CountingAiWorkerClient implements AiWorkerClient {

		private final AtomicInteger calls = new AtomicInteger();

		void reset() {
			calls.set(0);
		}

		int callCount() {
			return calls.get();
		}

		@Override
		public AiAnalysisResponse analyze(AiAnalysisRequest request) {
			calls.incrementAndGet();
			return new AiAnalysisResponse(
					request.analysisRequestId(), AnalysisStatus.SKIPPED, List.of(), List.of(), null, null);
		}
	}
}
