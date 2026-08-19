package com.suspiciouslions.backend.domain.ai.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.suspiciouslions.backend.domain.ai.event.MessageCreatedEvent;

@Component
public class AiAnalysisCoordinator {

	private static final Logger log = LoggerFactory.getLogger(AiAnalysisCoordinator.class);

	private final AiAnalysisService aiAnalysisService;
	private final Executor aiWorkerExecutor;
	private final ConcurrentHashMap<Long, RoomAnalysisState> roomStates = new ConcurrentHashMap<>();

	public AiAnalysisCoordinator(AiAnalysisService aiAnalysisService,
			@Qualifier("aiWorkerExecutor") Executor aiWorkerExecutor) {
		this.aiAnalysisService = aiAnalysisService;
		this.aiWorkerExecutor = aiWorkerExecutor;
	}

	public void submit(MessageCreatedEvent event) {
		AtomicBoolean shouldStart = new AtomicBoolean();
		roomStates.compute(event.chatRoomId(), (chatRoomId, state) -> {
			if (state == null) {
				shouldStart.set(true);
				return new RoomAnalysisState(event, System.nanoTime());
			}

			ScheduledAnalysis replaced = state.pending;
			state.pending = new ScheduledAnalysis(event, System.nanoTime());
			if (replaced == null) {
				log.info("AI analysis queued as latest pending request. chatRoomId={}, messageId={}",
						event.chatRoomId(), event.messageId());
			} else {
				log.info("AI analysis pending request coalesced. chatRoomId={}, discardedMessageId={}, latestMessageId={}",
						event.chatRoomId(), replaced.event().messageId(), event.messageId());
			}
			return state;
		});

		if (shouldStart.get()) {
			log.info("AI analysis scheduled immediately. chatRoomId={}, messageId={}",
					event.chatRoomId(), event.messageId());
			aiWorkerExecutor.execute(() -> drain(event.chatRoomId()));
		}
	}

	private void drain(Long chatRoomId) {
		while (true) {
			RoomAnalysisState state = roomStates.get(chatRoomId);
			if (state == null) {
				return;
			}

			ScheduledAnalysis current = state.current;
			long executorWaitMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - current.scheduledAtNanos());
			log.info("AI analysis executor task started. chatRoomId={}, messageId={}, executorWaitMillis={}",
					current.event().chatRoomId(), current.event().messageId(), executorWaitMillis);
			try {
				aiAnalysisService.analyze(current.event().chatRoomId(), current.event().messageId());
			} catch (RuntimeException exception) {
				log.warn("Unexpected AI analysis failure; continuing with the latest pending request. "
						+ "chatRoomId={}, messageId={}", current.event().chatRoomId(), current.event().messageId(), exception);
			}

			AtomicReference<ScheduledAnalysis> next = new AtomicReference<>();
			roomStates.compute(chatRoomId, (key, latestState) -> {
				if (latestState == null || latestState.pending == null) {
					return null;
				}
				latestState.current = latestState.pending;
				latestState.pending = null;
				next.set(latestState.current);
				return latestState;
			});

			if (next.get() == null) {
				return;
			}
		}
	}

	private static final class RoomAnalysisState {

		private ScheduledAnalysis current;
		private ScheduledAnalysis pending;

		private RoomAnalysisState(MessageCreatedEvent current, long scheduledAtNanos) {
			this.current = new ScheduledAnalysis(current, scheduledAtNanos);
		}
	}

	private record ScheduledAnalysis(MessageCreatedEvent event, long scheduledAtNanos) {
	}
}
