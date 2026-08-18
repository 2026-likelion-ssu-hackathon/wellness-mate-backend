package com.suspiciouslions.backend.domain.ai.client;

import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse;

@Component
public class RestAiWorkerClient implements AiWorkerClient {

	private final RestClient restClient;

	public RestAiWorkerClient(RestClient aiWorkerRestClient) {
		this.restClient = aiWorkerRestClient;
	}

	@Override
	public AiAnalysisResponse analyze(AiAnalysisRequest request) {
		AiAnalysisResponse response = restClient.post()
				.uri("/internal/v1/chat-analyses")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(AiAnalysisResponse.class);
		return Objects.requireNonNull(response, "AI worker returned an empty response body");
	}
}
