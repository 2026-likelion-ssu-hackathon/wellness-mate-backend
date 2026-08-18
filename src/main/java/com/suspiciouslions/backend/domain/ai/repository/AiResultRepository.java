package com.suspiciouslions.backend.domain.ai.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.suspiciouslions.backend.domain.ai.entity.AiResult;
import com.suspiciouslions.backend.domain.ai.entity.AiResultType;

public interface AiResultRepository extends JpaRepository<AiResult, Long> {

	List<AiResult> findByChatRoomIdAndResultTypeInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
			Long chatRoomId, Collection<AiResultType> resultTypes, OffsetDateTime createdAt, Pageable pageable);

	@Query(value = """
			SELECT result.*
			FROM ai_results result
			WHERE result.chat_room_id = :chatRoomId
			  AND (
			      result.visibility_type = 'COUPLE'
			      OR (result.visibility_type = 'INDIVIDUAL' AND result.recipient_user_id = :userId)
			  )
			  AND (CAST(:afterResultId AS BIGINT) IS NULL OR result.id > :afterResultId)
			  AND (
			      CAST(:triggerMessageId AS BIGINT) IS NULL
			      OR result.trigger_message_ids @> jsonb_build_array(CAST(:triggerMessageId AS BIGINT))
			  )
			ORDER BY result.id ASC
			""", nativeQuery = true)
	List<AiResult> findVisibleResults(
			@Param("chatRoomId") Long chatRoomId,
			@Param("userId") Long userId,
			@Param("afterResultId") Long afterResultId,
			@Param("triggerMessageId") Long triggerMessageId);
}
