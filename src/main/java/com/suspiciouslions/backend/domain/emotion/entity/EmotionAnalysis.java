package com.suspiciouslions.backend.domain.emotion.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
@Table(name = "emotion_analyses")
public class EmotionAnalysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "analysis_request_id", nullable = false)
	private UUID analysisRequestId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subject_user_id", nullable = false)
	private User subjectUser;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "viewer_user_id", nullable = false)
	private User viewerUser;

	@Enumerated(EnumType.STRING)
	@Column(name = "emotion_type", nullable = false)
	private EmotionType emotionType;

	@Column(name = "intensity_value", nullable = false)
	private BigDecimal intensityValue;

	@Column(name = "should_show", nullable = false)
	private boolean shouldShow;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "trigger_message_ids", nullable = false, columnDefinition = "jsonb")
	private List<Long> triggerMessageIds;

	@Column(name = "state_text")
	private String stateText;

	@Column(name = "detected_at", nullable = false, columnDefinition = "timestamptz")
	private OffsetDateTime detectedAt;

	@Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
	private OffsetDateTime expiresAt;

	protected EmotionAnalysis() {
	}

	public EmotionAnalysis(UUID analysisRequestId, ChatRoom chatRoom, User subjectUser, User viewerUser,
			EmotionType emotionType, BigDecimal intensityValue, boolean shouldShow, List<Long> triggerMessageIds,
			String stateText, OffsetDateTime detectedAt, OffsetDateTime expiresAt) {
		this.analysisRequestId = analysisRequestId;
		this.chatRoom = chatRoom;
		this.subjectUser = subjectUser;
		this.viewerUser = viewerUser;
		this.emotionType = emotionType;
		this.intensityValue = intensityValue;
		this.shouldShow = shouldShow;
		this.triggerMessageIds = triggerMessageIds;
		this.stateText = stateText;
		this.detectedAt = detectedAt;
		this.expiresAt = expiresAt;
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

	public User getSubjectUser() {
		return subjectUser;
	}

	public User getViewerUser() {
		return viewerUser;
	}

	public EmotionType getEmotionType() {
		return emotionType;
	}

	public BigDecimal getIntensityValue() {
		return intensityValue;
	}

	public boolean isShouldShow() {
		return shouldShow;
	}

	public List<Long> getTriggerMessageIds() {
		return triggerMessageIds;
	}

	public String getStateText() {
		return stateText;
	}

	public OffsetDateTime getDetectedAt() {
		return detectedAt;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}
}
