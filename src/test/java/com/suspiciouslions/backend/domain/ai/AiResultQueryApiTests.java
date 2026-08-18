package com.suspiciouslions.backend.domain.ai;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.suspiciouslions.backend.domain.ai.client.AiWorkerClient;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse.AnalysisStatus;
import com.suspiciouslions.backend.domain.ai.entity.AiResult;
import com.suspiciouslions.backend.domain.ai.entity.AiResultType;
import com.suspiciouslions.backend.domain.ai.entity.ContentType;
import com.suspiciouslions.backend.domain.ai.entity.VisibilityType;
import com.suspiciouslions.backend.domain.ai.repository.AiResultRepository;
import com.suspiciouslions.backend.domain.chat.entity.ChatRoom;
import com.suspiciouslions.backend.domain.chat.entity.RoomStatus;
import com.suspiciouslions.backend.domain.chat.repository.ChatRoomRepository;
import com.suspiciouslions.backend.domain.user.entity.User;
import com.suspiciouslions.backend.domain.user.repository.UserRepository;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(AiResultQueryApiTests.TestConfig.class)
class AiResultQueryApiTests {

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
	private AiResultRepository aiResultRepository;

	@Autowired
	private CountingAiWorkerClient aiWorkerClient;

	@BeforeEach
	void resetClientCount() {
		aiWorkerClient.reset();
	}

	@AfterEach
	void aiWorkerIsNeverCalled() {
		assertEquals(0, aiWorkerClient.callCount());
	}

	@Test
	void noQueryParametersReturnsOnlyVisibleRoomResultsInIdOrder() throws Exception {
		TestContext context = createTestContext();
		AiResult couple = saveResult(context.chatRoom(), null, VisibilityType.COUPLE,
				AiResultType.DATE_RECOMMENDATION, ContentType.MIXED, List.of(101L), Map.of("order", 1), 1);
		AiResult individualA = saveResult(context.chatRoom(), context.userA(), VisibilityType.INDIVIDUAL,
				AiResultType.TONE_CORRECTION, ContentType.TEXT, List.of(102L), Map.of("order", 2), 2);
		saveResult(context.chatRoom(), context.userB(), VisibilityType.INDIVIDUAL,
				AiResultType.YOUTUBE_RECOMMENDATION, ContentType.MIXED, List.of(103L), Map.of("order", 3), 3);
		saveResult(context.otherChatRoom(), null, VisibilityType.COUPLE,
				AiResultType.DATE_RECOMMENDATION, ContentType.MIXED, List.of(104L), Map.of("order", 4), 4);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].resultId").value(couple.getId()))
				.andExpect(jsonPath("$[1].resultId").value(individualA.getId()));
	}

	@Test
	void coupleResultIsVisibleToBothParticipants() throws Exception {
		TestContext context = createTestContext();
		AiResult couple = saveResult(context.chatRoom(), null, VisibilityType.COUPLE,
				AiResultType.YOUTUBE_RECOMMENDATION, ContentType.MIXED, List.of(101L), Map.of("videoId", "abc"), 1);

		for (User user : List.of(context.userA(), context.userB())) {
			mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
						.header("X-User-Id", user.getId()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].resultId").value(couple.getId()));
		}
	}

	@Test
	void individualResultsAreVisibleOnlyToTheirRecipients() throws Exception {
		TestContext context = createTestContext();
		AiResult resultA = saveResult(context.chatRoom(), context.userA(), VisibilityType.INDIVIDUAL,
				AiResultType.TONE_CORRECTION, ContentType.TEXT, List.of(101L), Map.of("target", "A"), 1);
		AiResult resultB = saveResult(context.chatRoom(), context.userB(), VisibilityType.INDIVIDUAL,
				AiResultType.TONE_CORRECTION, ContentType.TEXT, List.of(102L), Map.of("target", "B"), 2);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].resultId", contains(resultA.getId().intValue())));

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.userB().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].resultId", contains(resultB.getId().intValue())));
	}

	@Test
	void afterResultIdReturnsOnlyGreaterIds() throws Exception {
		TestContext context = createTestContext();
		AiResult first = saveCoupleResult(context, List.of(101L), 1);
		AiResult second = saveCoupleResult(context, List.of(102L), 2);
		AiResult third = saveCoupleResult(context, List.of(103L), 3);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("afterResultId", first.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].resultId",
						contains(second.getId().intValue(), third.getId().intValue())));
	}

	@Test
	void triggerMessageIdUsesJsonbArrayContainment() throws Exception {
		TestContext context = createTestContext();
		AiResult matching = saveCoupleResult(context, List.of(101L, 102L), 1);
		saveCoupleResult(context, List.of(103L), 2);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("triggerMessageId", "102"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].resultId", contains(matching.getId().intValue())));
	}

	@Test
	void bothQueryParametersReturnTheirIntersection() throws Exception {
		TestContext context = createTestContext();
		AiResult beforeCursor = saveCoupleResult(context, List.of(200L), 1);
		AiResult matching = saveCoupleResult(context, List.of(200L, 201L), 2);
		saveCoupleResult(context, List.of(201L), 3);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("afterResultId", beforeCursor.getId().toString())
					.param("triggerMessageId", "200"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].resultId", contains(matching.getId().intValue())));
	}

	@Test
	void noMatchingResultsReturnsEmptyArray() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void unknownUserReturnsNotFound() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", Long.MAX_VALUE))
				.andExpect(status().isNotFound());
	}

	@Test
	void unknownChatRoomReturnsNotFound() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", Long.MAX_VALUE)
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	void nonParticipantReturnsForbidden() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.outsider().getId()))
				.andExpect(status().isForbidden());
	}

	@Test
	void nonPositivePathHeaderAndQueryParametersReturnBadRequest() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", 0)
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", 0))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("afterResultId", "-1"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("triggerMessageId", "0"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void nestedResultDataIsReturnedWithoutFlattening() throws Exception {
		TestContext context = createTestContext();
		Map<String, Object> resultData = Map.of(
				"guideMessage", "추천 장소를 가져왔어요.",
				"mainPlace", Map.of(
						"name", "성수다락",
						"category", "RESTAURANT",
						"details", Map.of("tags", List.of("브런치", "데이트"))),
				"coursePlaces", List.of(
						Map.of("order", 1, "name", "성수다락"),
						Map.of("order", 2, "name", "서울숲"))
		);
		saveResult(context.chatRoom(), null, VisibilityType.COUPLE,
				AiResultType.DATE_RECOMMENDATION, ContentType.MIXED, List.of(101L), resultData, 1);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/ai-results", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].resultData.mainPlace.name").value("성수다락"))
				.andExpect(jsonPath("$[0].resultData.mainPlace.details.tags", contains("브런치", "데이트")))
				.andExpect(jsonPath("$[0].resultData.coursePlaces[1].name").value("서울숲"));
	}

	@Test
	void swaggerDocumentsEndpointParametersAndResponseDto() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/ai-results'].get.summary")
						.value("AI 결과 조회"))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/ai-results'].get.tags[0]")
						.value("AI Results"))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/ai-results'].get.parameters[*].name",
							hasItems("chatRoomId", "X-User-Id", "afterResultId", "triggerMessageId")))
				.andExpect(jsonPath("$.components.schemas.AiResultResponse.properties.resultType").exists())
				.andExpect(jsonPath("$.components.schemas.AiResultResponse.properties.visibilityType").exists())
				.andExpect(jsonPath("$.components.schemas.AiResultResponse.properties.contentType").exists())
				.andExpect(jsonPath("$.components.schemas.AiResultResponse.properties.resultData").exists())
				.andExpect(jsonPath("$.components.schemas.AiResultResponse.properties.resultType.enum",
							contains("TONE_CORRECTION", "DATE_RECOMMENDATION", "YOUTUBE_RECOMMENDATION")))
				.andExpect(jsonPath("$.components.schemas.AiResultResponse.properties.visibilityType.enum",
							contains("INDIVIDUAL", "COUPLE")))
				.andExpect(jsonPath("$.components.schemas.AiResultResponse.properties.contentType.enum",
							contains("TEXT", "LINK", "MIXED")));
	}

	private TestContext createTestContext() {
		User userA = saveUser("사용자 A");
		User userB = saveUser("사용자 B");
		User outsider = saveUser("외부 사용자");
		User otherPartner = saveUser("다른 상대방");
		ChatRoom chatRoom = saveChatRoom(userA, userB);
		ChatRoom otherChatRoom = saveChatRoom(outsider, otherPartner);
		return new TestContext(userA, userB, outsider, chatRoom, otherChatRoom);
	}

	private User saveUser(String nickname) {
		return userRepository.save(new User(null, null, nickname, null, time(0), time(0)));
	}

	private ChatRoom saveChatRoom(User userA, User userB) {
		return chatRoomRepository.save(new ChatRoom(
				userA, userB, LocalDate.of(2026, 8, 1), RoomStatus.ACTIVE, time(0), null));
	}

	private AiResult saveCoupleResult(TestContext context, List<Long> triggerMessageIds, int minute) {
		return saveResult(context.chatRoom(), null, VisibilityType.COUPLE,
				AiResultType.DATE_RECOMMENDATION, ContentType.MIXED,
				triggerMessageIds, Map.of("minute", minute), minute);
	}

	private AiResult saveResult(ChatRoom chatRoom, User recipientUser, VisibilityType visibilityType,
			AiResultType resultType, ContentType contentType, List<Long> triggerMessageIds,
			Map<String, Object> resultData, int minute) {
		return aiResultRepository.save(new AiResult(
				UUID.randomUUID(), chatRoom, recipientUser, resultType, visibilityType, contentType,
				triggerMessageIds, resultData, time(minute)));
	}

	private OffsetDateTime time(int minute) {
		return OffsetDateTime.of(2026, 8, 18, 16, 0, 0, 0, ZoneOffset.ofHours(9)).plusMinutes(minute);
	}

	private record TestContext(
			User userA,
			User userB,
			User outsider,
			ChatRoom chatRoom,
			ChatRoom otherChatRoom
	) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

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
