package com.suspiciouslions.backend.domain.emotion.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.suspiciouslions.backend.domain.emotion.entity.EmotionAnalysis;

public interface EmotionAnalysisRepository extends JpaRepository<EmotionAnalysis, Long> {

	@Query("""
			select emotion
			from EmotionAnalysis emotion
			join fetch emotion.chatRoom
			join fetch emotion.subjectUser
			where emotion.chatRoom.id = :chatRoomId
			  and emotion.viewerUser.id = :viewerUserId
			  and emotion.shouldShow = true
			  and emotion.expiresAt > :now
			  and not exists (
			      select newer.id
			      from EmotionAnalysis newer
			      where newer.chatRoom.id = emotion.chatRoom.id
			        and newer.subjectUser.id = emotion.subjectUser.id
			        and newer.viewerUser.id = emotion.viewerUser.id
			        and newer.shouldShow = true
			        and newer.expiresAt > :now
			        and (
			            newer.detectedAt > emotion.detectedAt
			            or (newer.detectedAt = emotion.detectedAt and newer.id > emotion.id)
			        )
			  )
			order by emotion.subjectUser.id asc
			""")
	List<EmotionAnalysis> findCurrentVisibleStates(
			@Param("chatRoomId") Long chatRoomId,
			@Param("viewerUserId") Long viewerUserId,
			@Param("now") OffsetDateTime now);
}
