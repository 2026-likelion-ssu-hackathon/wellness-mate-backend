package com.suspiciouslions.backend.domain.chat.entity;

import java.time.OffsetDateTime;

import com.suspiciouslions.backend.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class Message {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_id", nullable = false)
	private User sender;

	@Column(name = "client_message_id", nullable = false)
	private String clientMessageId;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "sent_at", nullable = false, columnDefinition = "timestamptz")
	private OffsetDateTime sentAt;

	protected Message() {
	}

	public Message(ChatRoom chatRoom, User sender, String clientMessageId, String content, OffsetDateTime sentAt) {
		this.chatRoom = chatRoom;
		this.sender = sender;
		this.clientMessageId = clientMessageId;
		this.content = content;
		this.sentAt = sentAt;
	}

	public Long getId() {
		return id;
	}

	public ChatRoom getChatRoom() {
		return chatRoom;
	}

	public User getSender() {
		return sender;
	}

	public String getClientMessageId() {
		return clientMessageId;
	}

	public String getContent() {
		return content;
	}

	public OffsetDateTime getSentAt() {
		return sentAt;
	}
}
