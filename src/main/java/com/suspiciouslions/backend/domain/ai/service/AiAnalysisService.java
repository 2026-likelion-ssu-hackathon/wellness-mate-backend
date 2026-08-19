package com.suspiciouslions.backend.domain.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.suspiciouslions.backend.domain.ai.client.AiWorkerClient;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse;

@Service
public class AiAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

	private final AiAnalysisRequestService requestService;
	private final AiWorkerClient aiWorkerClient;
	private final AiAnalysisResponseService responseService;

	public AiAnalysisService(AiAnalysisRequestService requestService, AiWorkerClient aiWorkerClient,
			AiAnalysisResponseService responseService) {
		this.requestService = requestService;
		this.aiWorkerClient = aiWorkerClient;
		this.responseService = responseService;
	}

	public void analyze(Long chatRoomId, Long messageId) {
		try {
			AiAnalysisRequest request = requestService.create(chatRoomId);
			log.info("AI analysis started. chatRoomId={}, messageId={}, analysisRequestId={}",
					chatRoomId, messageId, request.analysisRequestId());
			AiAnalysisResponse response = aiWorkerClient.analyze(request);
			responseService.process(request, response);
			log.info("AI analysis completed. chatRoomId={}, messageId={}, analysisRequestId={}, status={}",
					chatRoomId, messageId, request.analysisRequestId(), response.status());
		} catch (RuntimeException exception) {
			log.warn("AI analysis failed without affecting the saved message. chatRoomId={}, messageId={}",
					chatRoomId, messageId, exception);
		}
	}
}
