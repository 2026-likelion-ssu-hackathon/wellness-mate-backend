package com.suspiciouslions.backend.domain.ai.service;

import java.util.concurrent.TimeUnit;

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
		long analysisStartedAtNanos = System.nanoTime();
		try {
			AiAnalysisRequest request = requestService.create(chatRoomId);
			long requestPreparedAtNanos = System.nanoTime();
			log.info("AI worker request started. chatRoomId={}, messageId={}, analysisRequestId={}, requestBuildMillis={}",
					chatRoomId, messageId, request.analysisRequestId(),
					elapsedMillis(analysisStartedAtNanos, requestPreparedAtNanos));
			AiAnalysisResponse response = aiWorkerClient.analyze(request);
			long workerRespondedAtNanos = System.nanoTime();
			log.info("AI worker response received. chatRoomId={}, messageId={}, analysisRequestId={}, workerCallMillis={}",
					chatRoomId, messageId, request.analysisRequestId(),
					elapsedMillis(requestPreparedAtNanos, workerRespondedAtNanos));
			responseService.process(request, response);
			long persistenceCompletedAtNanos = System.nanoTime();
			log.info("AI analysis completed. chatRoomId={}, messageId={}, analysisRequestId={}, status={}, persistenceMillis={}, totalMillis={}",
					chatRoomId, messageId, request.analysisRequestId(), response.status(),
					elapsedMillis(workerRespondedAtNanos, persistenceCompletedAtNanos),
					elapsedMillis(analysisStartedAtNanos, persistenceCompletedAtNanos));
		} catch (RuntimeException exception) {
			log.warn("AI analysis failed without affecting the saved message. chatRoomId={}, messageId={}",
					chatRoomId, messageId, exception);
		}
	}

	private long elapsedMillis(long startedAtNanos, long endedAtNanos) {
		return TimeUnit.NANOSECONDS.toMillis(endedAtNanos - startedAtNanos);
	}
}
