package com.suspiciouslions.backend.domain.ai.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.suspiciouslions.backend.domain.chat.entity.ChatRoom;
import com.suspiciouslions.backend.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_results")
public class AiResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "analysis_request_id", nullable = false)
	private UUID analysisRequestId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipient_user_id")
	private User recipientUser;

	@Enumerated(EnumType.STRING)
	@Column(name = "result_type", nullable = false)
	private AiResultType resultType;

	@Enumerated(EnumType.STRING)
	@Column(name = "visibility_type", nullable = false)
	private VisibilityType visibilityType;

	@Enumerated(EnumType.STRING)
	@Column(name = "content_type", nullable = false)
	private ContentType contentType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "trigger_message_ids", nullable = false, columnDefinition = "jsonb")
	private List<Long> triggerMessageIds;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "result_data", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> resultData;

	@Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
	private OffsetDateTime createdAt;

	protected AiResult() {
	}

	public AiResult(UUID analysisRequestId, ChatRoom chatRoom, User recipientUser, AiResultType resultType,
			VisibilityType visibilityType, ContentType contentType, List<Long> triggerMessageIds,
			Map<String, Object> resultData, OffsetDateTime createdAt) {
		this.analysisRequestId = analysisRequestId;
		this.chatRoom = chatRoom;
		this.recipientUser = recipientUser;
		this.resultType = resultType;
		this.visibilityType = visibilityType;
		this.contentType = contentType;
		this.triggerMessageIds = triggerMessageIds;
		this.resultData = resultData;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public UUID getAnalysisRequestId() {
		return analysisRequestId;
	}

	public ChatRoom getChatRoom() {
		return chatRoom;
	}

	public User getRecipientUser() {
		return recipientUser;
	}

	public AiResultType getResultType() {
		return resultType;
	}

	public VisibilityType getVisibilityType() {
		return visibilityType;
	}

	public ContentType getContentType() {
		return contentType;
	}

	public List<Long> getTriggerMessageIds() {
		return triggerMessageIds;
	}

	public Map<String, Object> getResultData() {
		return resultData;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
