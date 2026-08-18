package com.suspiciouslions.backend.domain.ai;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.suspiciouslions.backend.domain.ai.client.RestAiWorkerClient;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest.AnalysisMessage;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest.Participant;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisRequest.ParticipantKey;
import com.suspiciouslions.backend.domain.ai.dto.AiAnalysisResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestAiWorkerClientTests {

	@Test
	void sendsConfirmedRequestJsonToAiWorker() {
		UUID requestId = UUID.fromString("c783ec30-8a45-4c65-a542-f865c71a2f01");
		RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestAiWorkerClient client = new RestAiWorkerClient(builder.build());
		AiAnalysisRequest request = new AiAnalysisRequest(
				requestId,
				1L,
				List.of(new Participant(ParticipantKey.USER_A), new Participant(ParticipantKey.USER_B)),
				List.of(new AnalysisMessage(
						101L,
						ParticipantKey.USER_A,
						"왜 연락을 안 했어?",
						OffsetDateTime.parse("2026-08-18T16:00:00+09:00")
				)),
				List.of()
		);

		server.expect(once(), requestTo("http://localhost:8000/internal/v1/chat-analyses"))
				.andExpect(method(org.springframework.http.HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.analysisRequestId").value(requestId.toString()))
				.andExpect(jsonPath("$.chatRoomId").value(1))
				.andExpect(jsonPath("$.participants[0].participantKey").value("USER_A"))
				.andExpect(jsonPath("$.participants[1].participantKey").value("USER_B"))
				.andExpect(jsonPath("$.messages[0].messageId").value(101))
				.andExpect(jsonPath("$.messages[0].sender").value("USER_A"))
				.andExpect(jsonPath("$.messages[0].content").value("왜 연락을 안 했어?"))
				.andExpect(jsonPath("$.recentResults").isArray())
				.andExpect(jsonPath("$.recentResults").isEmpty())
				.andExpect(jsonPath("$.speakerProfiles").doesNotExist())
				.andRespond(withSuccess("""
						{
						  "analysisRequestId": "c783ec30-8a45-4c65-a542-f865c71a2f01",
						  "status": "SKIPPED",
						  "results": [],
						  "emotionAnalyses": []
						}
						""", MediaType.APPLICATION_JSON));

		AiAnalysisResponse response = client.analyze(request);

		assertEquals(requestId, response.analysisRequestId());
		assertEquals(AiAnalysisResponse.AnalysisStatus.SKIPPED, response.status());
		server.verify();
	}
}
