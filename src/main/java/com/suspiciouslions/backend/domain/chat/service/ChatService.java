package com.suspiciouslions.backend.domain.chat.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.suspiciouslions.backend.domain.chat.dto.ChatRoomResponse;
import com.suspiciouslions.backend.domain.chat.dto.ChatRoomResponse.PartnerResponse;
import com.suspiciouslions.backend.domain.chat.dto.MessageResponse;
import com.suspiciouslions.backend.domain.chat.dto.SendMessageRequest;
import com.suspiciouslions.backend.domain.chat.entity.ChatRoom;
import com.suspiciouslions.backend.domain.chat.entity.Message;
import com.suspiciouslions.backend.domain.chat.repository.ChatRoomRepository;
import com.suspiciouslions.backend.domain.chat.repository.MessageRepository;
import com.suspiciouslions.backend.domain.user.entity.User;
import com.suspiciouslions.backend.domain.user.repository.UserRepository;

@Service
public class ChatService {

	private final UserRepository userRepository;
	private final ChatRoomRepository chatRoomRepository;
	private final MessageRepository messageRepository;
	private final TransactionTemplate transactionTemplate;

	public ChatService(UserRepository userRepository, ChatRoomRepository chatRoomRepository,
			MessageRepository messageRepository, PlatformTransactionManager transactionManager) {
		this.userRepository = userRepository;
		this.chatRoomRepository = chatRoomRepository;
		this.messageRepository = messageRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Transactional(readOnly = true)
	public ChatRoomResponse getChatRoom(Long chatRoomId, Long userId) {
		ChatRoom chatRoom = findChatRoomForUser(chatRoomId, userId);
		User partner = Objects.equals(chatRoom.getUserA().getId(), userId)
				? chatRoom.getUserB()
				: chatRoom.getUserA();

		return new ChatRoomResponse(
				chatRoom.getId(),
				chatRoom.getRoomStatus(),
				chatRoom.getRelationshipStartedOn(),
				new PartnerResponse(partner.getId(), partner.getNickname(), partner.getProfileImageUrl())
		);
	}

	public MessageResponse sendMessage(Long chatRoomId, Long userId, SendMessageRequest request) {
		User sender = findUser(userId);
		ChatRoom chatRoom = findChatRoom(chatRoomId);
		validateParticipant(chatRoom, userId);

		return messageRepository.findBySenderIdAndClientMessageId(userId, request.clientMessageId())
				.map(existing -> existingMessageForRoom(existing, chatRoomId))
				.orElseGet(() -> saveMessageIdempotently(chatRoom, sender, request));
	}

	@Transactional(readOnly = true)
	public List<MessageResponse> getMessages(Long chatRoomId, Long userId, Long beforeMessageId,
			Long afterMessageId, int size) {
		findChatRoomForUser(chatRoomId, userId);
		PageRequest limit = PageRequest.of(0, size);

		if (beforeMessageId != null) {
			List<Message> messages = new ArrayList<>(messageRepository
					.findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoomId, beforeMessageId, limit));
			Collections.reverse(messages);
			return messages.stream().map(this::toResponse).toList();
		}

		if (afterMessageId != null) {
			return messageRepository
					.findByChatRoomIdAndIdGreaterThanOrderByIdAsc(chatRoomId, afterMessageId, limit)
					.stream()
					.map(this::toResponse)
					.toList();
		}

		List<Message> messages = new ArrayList<>(
				messageRepository.findByChatRoomIdOrderByIdDesc(chatRoomId, limit));
		Collections.reverse(messages);
		return messages.stream().map(this::toResponse).toList();
	}

	private MessageResponse saveMessageIdempotently(ChatRoom chatRoom, User sender, SendMessageRequest request) {
		try {
			Message saved = transactionTemplate.execute(status -> messageRepository.saveAndFlush(new Message(
					chatRoom,
					sender,
					request.clientMessageId(),
					request.content(),
					request.sentAt()
			)));
			return toResponse(Objects.requireNonNull(saved));
		} catch (DataIntegrityViolationException exception) {
			return messageRepository.findBySenderIdAndClientMessageId(sender.getId(), request.clientMessageId())
					.map(existing -> existingMessageForRoom(existing, chatRoom.getId()))
					.orElseThrow(() -> exception);
		}
	}

	private MessageResponse existingMessageForRoom(Message message, Long requestedChatRoomId) {
		if (!Objects.equals(message.getChatRoom().getId(), requestedChatRoomId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"clientMessageId is already used in another chat room");
		}
		return toResponse(message);
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
	}

	private ChatRoom findChatRoom(Long chatRoomId) {
		return chatRoomRepository.findWithUsersById(chatRoomId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));
	}

	private ChatRoom findChatRoomForUser(Long chatRoomId, Long userId) {
		findUser(userId);
		ChatRoom chatRoom = findChatRoom(chatRoomId);
		validateParticipant(chatRoom, userId);
		return chatRoom;
	}

	private void validateParticipant(ChatRoom chatRoom, Long userId) {
		if (!Objects.equals(chatRoom.getUserA().getId(), userId)
				&& !Objects.equals(chatRoom.getUserB().getId(), userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a chat room participant");
		}
	}

	private MessageResponse toResponse(Message message) {
		return new MessageResponse(
				message.getId(),
				message.getChatRoom().getId(),
				message.getSender().getId(),
				message.getClientMessageId(),
				message.getContent(),
				message.getSentAt()
		);
	}
}
