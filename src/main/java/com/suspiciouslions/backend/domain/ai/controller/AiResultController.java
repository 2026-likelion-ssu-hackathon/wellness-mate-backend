package com.suspiciouslions.backend.domain.ai.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.suspiciouslions.backend.domain.ai.dto.AiResultResponse;
import com.suspiciouslions.backend.domain.ai.service.AiResultQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/chat-rooms/{chatRoomId}/ai-results")
public class AiResultController {

	private static final String SUCCESS_EXAMPLE = """
			[
			  {
			    "resultId": 201,
			    "chatRoomId": 1,
			    "resultType": "DATE_RECOMMENDATION",
			    "visibilityType": "COUPLE",
			    "contentType": "MIXED",
			    "triggerMessageIds": [101, 102],
			    "resultData": {
			      "guideMessage": "추천 장소를 가져왔어요.",
			      "mainPlace": {
			        "name": "성수다락",
			        "category": "RESTAURANT"
			      }
			    },
			    "createdAt": "2026-08-18T16:00:00+09:00"
			  }
			]
			""";

	private final AiResultQueryService aiResultQueryService;

	public AiResultController(AiResultQueryService aiResultQueryService) {
		this.aiResultQueryService = aiResultQueryService;
	}

	@Operation(summary = "AI 결과 조회",
			description = "채팅방 참여자에게 허용된 저장 AI 결과를 ID 오름차순으로 조회합니다.",
			tags = "AI Results")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "AI 결과 조회 성공",
					content = @Content(array = @ArraySchema(schema = @Schema(implementation = AiResultResponse.class)),
							examples = @ExampleObject(value = SUCCESS_EXAMPLE))),
			@ApiResponse(responseCode = "400", description = "Path, Header 또는 Query Parameter 형식 오류",
					content = @Content),
			@ApiResponse(responseCode = "403", description = "요청 사용자가 채팅방 참여자가 아님",
					content = @Content),
			@ApiResponse(responseCode = "404", description = "사용자 또는 채팅방을 찾을 수 없음",
					content = @Content)
	})
	@GetMapping
	public List<AiResultResponse> getResults(
			@Parameter(description = "채팅방 ID", required = true, example = "1")
			@PathVariable @Positive Long chatRoomId,
			@Parameter(name = "X-User-Id", description = "임시 사용자 ID", required = true,
					example = "1", in = ParameterIn.HEADER)
			@RequestHeader("X-User-Id") @Positive Long userId,
			@Parameter(description = "선택값. 해당 ID보다 큰 결과만 조회", example = "200")
			@RequestParam(required = false) @Positive Long afterResultId,
			@Parameter(description = "선택값. 해당 메시지 ID가 트리거인 결과만 조회", example = "101")
			@RequestParam(required = false) @Positive Long triggerMessageId) {
		return aiResultQueryService.getResults(chatRoomId, userId, afterResultId, triggerMessageId);
	}
}
