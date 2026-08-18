package com.suspiciouslions.backend.domain.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suspiciouslions.backend.domain.ai.entity.AiResult;

public interface AiResultRepository extends JpaRepository<AiResult, Long> {
}
