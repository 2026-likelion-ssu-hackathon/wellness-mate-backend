package com.suspiciouslions.backend.domain.emotion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suspiciouslions.backend.domain.emotion.entity.EmotionAnalysis;

public interface EmotionAnalysisRepository extends JpaRepository<EmotionAnalysis, Long> {
}
