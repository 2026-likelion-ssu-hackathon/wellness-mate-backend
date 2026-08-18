package com.suspiciouslions.backend.domain.ai.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.suspiciouslions.backend.domain.ai.entity.AiResult;
import com.suspiciouslions.backend.domain.ai.entity.AiResultType;

public interface AiResultRepository extends JpaRepository<AiResult, Long> {

	List<AiResult> findByChatRoomIdAndResultTypeInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
			Long chatRoomId, Collection<AiResultType> resultTypes, OffsetDateTime createdAt, Pageable pageable);
}
