package com.suspiciouslions.backend.domain.ai.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse.AnalysisStatus;

@Service
public class AiAnalysisResponseService {

	private static final Logger log = LoggerFactory.getLogger(AiAnalysisResponseService.class);

	private final AiResultPersistenceService aiResultPersistenceService;
	private final EmotionAnalysisPersistenceService emotionAnalysisPersistenceService;

	public AiAnalysisResponseService(AiResultPersistenceService aiResultPersistenceService,
			EmotionAnalysisPersistenceService emotionAnalysisPersistenceService) {
		this.aiResultPersistenceService = aiResultPersistenceService;
		this.emotionAnalysisPersistenceService = emotionAnalysisPersistenceService;
	}

	public void process(AiAnalysisRequest request, AiAnalysisResponse response) {
		Objects.requireNonNull(response, "response is required");
		if (!Objects.equals(request.analysisRequestId(), response.analysisRequestId())) {
			throw new IllegalArgumentException("analysisRequestId does not match the request");
		}
		Objects.requireNonNull(response.status(), "status is required");

		if (response.status() == AnalysisStatus.FAILED) {
			log.warn("AI worker reported FAILED. analysisRequestId={}, errorCode={}",
					response.analysisRequestId(), response.errorCode());
			return;
		}

		try {
			aiResultPersistenceService.save(request, response.results());
		} catch (RuntimeException exception) {
			log.warn("Rejected AI results. analysisRequestId={}", request.analysisRequestId(), exception);
		}

		try {
			emotionAnalysisPersistenceService.save(request, response.emotionAnalyses());
		} catch (RuntimeException exception) {
			log.warn("Rejected emotion analyses. analysisRequestId={}", request.analysisRequestId(), exception);
		}
	}
}
