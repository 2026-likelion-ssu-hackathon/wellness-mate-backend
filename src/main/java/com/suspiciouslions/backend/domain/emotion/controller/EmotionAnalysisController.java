package com.suspiciouslions.backend.domain.emotion.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suspiciouslions.backend.domain.emotion.dto.EmotionAnalysisResponse;
import com.suspiciouslions.backend.domain.emotion.service.EmotionAnalysisQueryService;

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
@RequestMapping("/api/chat-rooms/{chatRoomId}/emotion-analyses")
public class EmotionAnalysisController {

	private static final String SUCCESS_EXAMPLE = """
			[
			  {
			    "emotionAnalysisId": 301,
			    "chatRoomId": 1,
			    "subjectUserId": 2,
			    "emotionType": "STABLE",
			    "intensityValue": 0.0,
			    "stateText": "평온해요",
			    "detectedAt": "2026-08-18T16:00:00+09:00",
			    "expiresAt": "2026-08-18T16:40:00+09:00"
			  }
			]
			""";

	private final EmotionAnalysisQueryService emotionAnalysisQueryService;

	public EmotionAnalysisController(EmotionAnalysisQueryService emotionAnalysisQueryService) {
		this.emotionAnalysisQueryService = emotionAnalysisQueryService;
	}

	@Operation(summary = "감정 상태 조회",
			description = "채팅방 참여자에게 허용된, 만료되지 않은 최신 표시 감정 상태를 subject별로 조회합니다. "
					+ "shouldShow=false 분석은 현재 표시 상태를 갱신하거나 제거하지 않습니다.",
			tags = "Emotion Analyses")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "감정 상태 조회 성공. 표시할 상태가 없으면 빈 배열 반환",
					content = @Content(array = @ArraySchema(
							schema = @Schema(implementation = EmotionAnalysisResponse.class)),
							examples = @ExampleObject(value = SUCCESS_EXAMPLE))),
			@ApiResponse(responseCode = "400", description = "Path 또는 X-User-Id 형식 오류", content = @Content),
			@ApiResponse(responseCode = "403", description = "요청 사용자가 채팅방 참여자가 아님", content = @Content),
			@ApiResponse(responseCode = "404", description = "사용자 또는 채팅방을 찾을 수 없음", content = @Content)
	})
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public List<EmotionAnalysisResponse> getCurrentStates(
			@Parameter(description = "감정 상태를 조회할 채팅방 ID", required = true, example = "1")
			@PathVariable @Positive Long chatRoomId,
			@Parameter(name = "X-User-Id", description = "감정 상태를 보는 현재 임시 사용자 ID", required = true,
					example = "1", in = ParameterIn.HEADER)
			@RequestHeader("X-User-Id") @Positive Long userId) {
		return emotionAnalysisQueryService.getCurrentStates(chatRoomId, userId);
	}
}
