package com.suspiciouslions.backend.domain.ai.client;

import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse;

public interface AiWorkerClient {

	AiAnalysisResponse analyze(AiAnalysisRequest request);
}
