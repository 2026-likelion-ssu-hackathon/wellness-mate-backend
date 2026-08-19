package com.suspiciouslions.backend.domain.ai.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.suspiciouslions.backend.domain.ai.service.AiAnalysisCoordinator;

@Component
public class MessageCreatedEventListener {

	private final AiAnalysisCoordinator aiAnalysisCoordinator;

	public MessageCreatedEventListener(AiAnalysisCoordinator aiAnalysisCoordinator) {
		this.aiAnalysisCoordinator = aiAnalysisCoordinator;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(MessageCreatedEvent event) {
		aiAnalysisCoordinator.submit(event);
	}
}
