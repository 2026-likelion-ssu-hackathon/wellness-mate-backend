package com.suspiciouslions.backend.domain.chat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.suspiciouslions.backend.domain.chat.entity.ChatRoom;
import com.suspiciouslions.backend.domain.chat.entity.Message;
import com.suspiciouslions.backend.domain.chat.entity.RoomStatus;
import com.suspiciouslions.backend.domain.chat.repository.ChatRoomRepository;
import com.suspiciouslions.backend.domain.chat.repository.MessageRepository;
import com.suspiciouslions.backend.domain.user.entity.User;
import com.suspiciouslions.backend.domain.user.repository.UserRepository;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class ChatApiTests {

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
	private MessageRepository messageRepository;

	@Test
	void participantGetsChatRoomAndPartner() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.chatRoomId").value(context.chatRoom().getId()))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.relationshipStartedOn").value("2026-08-01"))
				.andExpect(jsonPath("$.partner.userId").value(context.userB().getId()))
				.andExpect(jsonPath("$.partner.nickname").value("사용자 B"))
				.andExpect(jsonPath("$.partner.profileImageUrl").value("https://example.com/b.png"));
	}

	@Test
	void nonParticipantCannotGetChatRoom() throws Exception {
		TestContext context = createTestContext();
		User outsider = saveUser("외부 사용자", null);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}", context.chatRoom().getId())
					.header("X-User-Id", outsider.getId()))
				.andExpect(status().isForbidden());
	}

	@Test
	void messageIsSavedAndReturned() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(post("/api/chat-rooms/{chatRoomId}/messages", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content(messageRequest("client-1", "안녕하세요", time(1))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messageId").isNumber())
				.andExpect(jsonPath("$.chatRoomId").value(context.chatRoom().getId()))
				.andExpect(jsonPath("$.senderId").value(context.userA().getId()))
				.andExpect(jsonPath("$.clientMessageId").value("client-1"))
				.andExpect(jsonPath("$.content").value("안녕하세요"))
				.andExpect(jsonPath("$.sentAt").value("2026-08-18T03:01:00Z"));

		org.junit.jupiter.api.Assertions.assertEquals(1, messageRepository.count());
	}

	@Test
	void retryWithSameClientMessageIdReturnsExistingMessage() throws Exception {
		TestContext context = createTestContext();

		String firstResponse = mockMvc.perform(post("/api/chat-rooms/{chatRoomId}/messages", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content(messageRequest("same-client-id", "첫 요청", time(1))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(post("/api/chat-rooms/{chatRoomId}/messages", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content(messageRequest("same-client-id", "재전송 본문", time(2))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("첫 요청"));

		org.junit.jupiter.api.Assertions.assertEquals(1, messageRepository.count());
		org.junit.jupiter.api.Assertions.assertTrue(firstResponse.contains("\"content\":\"첫 요청\""));
	}

	@Test
	void blankMessageContentIsRejected() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(post("/api/chat-rooms/{chatRoomId}/messages", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content(messageRequest("blank-message", "   ", time(1))))
				.andExpect(status().isBadRequest());

		org.junit.jupiter.api.Assertions.assertEquals(0, messageRepository.count());
	}

	@Test
	void latestMessagesUseLimitAndReturnOldestToNewest() throws Exception {
		TestContext context = createTestContext();
		List<Message> messages = saveMessages(context, 5);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/messages", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("size", "3"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].messageId").value(messages.get(2).getId()))
				.andExpect(jsonPath("$[2].messageId").value(messages.get(4).getId()));
	}

	@Test
	void beforeCursorReturnsOlderMessagesOldestToNewest() throws Exception {
		TestContext context = createTestContext();
		List<Message> messages = saveMessages(context, 5);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/messages", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("beforeMessageId", messages.get(4).getId().toString())
					.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].messageId").value(messages.get(2).getId()))
				.andExpect(jsonPath("$[1].messageId").value(messages.get(3).getId()));
	}

	@Test
	void afterCursorReturnsNewerMessagesOldestToNewest() throws Exception {
		TestContext context = createTestContext();
		List<Message> messages = saveMessages(context, 5);

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/messages", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("afterMessageId", messages.get(1).getId().toString())
					.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].messageId").value(messages.get(2).getId()))
				.andExpect(jsonPath("$[1].messageId").value(messages.get(3).getId()));
	}

	@Test
	void bothCursorsAreRejected() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/messages", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("beforeMessageId", "10")
					.param("afterMessageId", "20"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void sizeOutsideAllowedRangeIsRejected() throws Exception {
		TestContext context = createTestContext();

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/messages", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("size", "0"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/chat-rooms/{chatRoomId}/messages", context.chatRoom().getId())
					.header("X-User-Id", context.userA().getId())
					.param("size", "101"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void chatEndpointsAreDocumentedInOpenApi() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}'].get.summary").value("채팅방 조회"))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/messages'].post.requestBody.required")
						.value(true))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/messages'].get.parameters[*].name")
						.value(hasItem("beforeMessageId")))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/messages'].get.parameters[*].name")
						.value(hasItem("afterMessageId")))
				.andExpect(jsonPath("$.paths['/api/chat-rooms/{chatRoomId}/messages'].get.parameters[*].name")
						.value(hasItem("size")));
	}

	private TestContext createTestContext() {
		User userA = saveUser("사용자 A", "https://example.com/a.png");
		User userB = saveUser("사용자 B", "https://example.com/b.png");
		ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(
				userA,
				userB,
				LocalDate.of(2026, 8, 1),
				RoomStatus.ACTIVE,
				time(0),
				null
		));
		return new TestContext(userA, userB, chatRoom);
	}

	private User saveUser(String nickname, String profileImageUrl) {
		return userRepository.save(new User(null, null, nickname, profileImageUrl, time(0), time(0)));
	}

	private List<Message> saveMessages(TestContext context, int count) {
		List<Message> messages = new ArrayList<>();
		for (int index = 1; index <= count; index++) {
			messages.add(messageRepository.save(new Message(
					context.chatRoom(),
					context.userA(),
					"client-" + index,
					"메시지 " + index,
					time(index)
			)));
		}
		return messages;
	}

	private String messageRequest(String clientMessageId, String content, OffsetDateTime sentAt) {
		return """
				{
				  "clientMessageId": "%s",
				  "content": "%s",
				  "sentAt": "%s"
				}
				""".formatted(clientMessageId, content, sentAt);
	}

	private OffsetDateTime time(int minute) {
		return OffsetDateTime.of(2026, 8, 18, 12, 0, 0, 0, ZoneOffset.ofHours(9)).plusMinutes(minute);
	}

	private record TestContext(User userA, User userB, ChatRoom chatRoom) {
	}
}
