package com.suspiciouslions.backend.domain.ai.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.suspiciouslions.backend.domain.ai.service.AiAnalysisService;

@Component
public class MessageCreatedEventListener {

	private final AiAnalysisService aiAnalysisService;

	public MessageCreatedEventListener(AiAnalysisService aiAnalysisService) {
		this.aiAnalysisService = aiAnalysisService;
	}

	@Async("aiWorkerExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(MessageCreatedEvent event) {
		aiAnalysisService.analyze(event.chatRoomId(), event.messageId());
	}
}
