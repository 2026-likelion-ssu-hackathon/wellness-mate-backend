package com.suspiciouslions.backend.domain.ai;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.suspiciouslions.backend.domain.ai.event.MessageCreatedEvent;
import com.suspiciouslions.backend.domain.ai.service.AiAnalysisCoordinator;
import com.suspiciouslions.backend.domain.ai.service.AiAnalysisService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiAnalysisCoordinatorTests {

	private final ExecutorService executor = Executors.newFixedThreadPool(4);

	@AfterEach
	void shutdownExecutor() throws InterruptedException {
		executor.shutdownNow();
		assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
	}

	@Test
	void idleRoomSubmitsItsFirstRequestImmediately() {
		AiAnalysisService analysisService = mock(AiAnalysisService.class);
		AiAnalysisCoordinator coordinator = new AiAnalysisCoordinator(analysisService, Runnable::run);

		coordinator.submit(new MessageCreatedEvent(1L, 10L));

		verify(analysisService).analyze(10L, 1L);
	}

	@Test
	void sameRoomKeepsRunningRequestAndOnlyLatestPendingRequest() throws InterruptedException {
		AiAnalysisService analysisService = mock(AiAnalysisService.class);
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(2);
		List<Long> analyzedMessageIds = new CopyOnWriteArrayList<>();
		doAnswer(invocation -> {
			Long messageId = invocation.getArgument(1);
			analyzedMessageIds.add(messageId);
			if (messageId == 1L) {
				firstStarted.countDown();
				assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
			}
			finished.countDown();
			return null;
		}).when(analysisService).analyze(org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong());
		AiAnalysisCoordinator coordinator = new AiAnalysisCoordinator(analysisService, executor);

		coordinator.submit(new MessageCreatedEvent(1L, 10L));
		assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
		coordinator.submit(new MessageCreatedEvent(2L, 10L));
		coordinator.submit(new MessageCreatedEvent(3L, 10L));
		coordinator.submit(new MessageCreatedEvent(4L, 10L));
		releaseFirst.countDown();

		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(List.of(1L, 4L), analyzedMessageIds);
	}

	@Test
	void differentRoomsCanRunIndependently() throws InterruptedException {
		AiAnalysisService analysisService = mock(AiAnalysisService.class);
		CountDownLatch bothStarted = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		doAnswer(invocation -> {
			bothStarted.countDown();
			assertTrue(release.await(5, TimeUnit.SECONDS));
			return null;
		}).when(analysisService).analyze(org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong());
		AiAnalysisCoordinator coordinator = new AiAnalysisCoordinator(analysisService, executor);

		coordinator.submit(new MessageCreatedEvent(1L, 10L));
		coordinator.submit(new MessageCreatedEvent(2L, 20L));

		assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
		release.countDown();
	}

	@Test
	void pendingRequestRunsAfterPreviousAnalysisFailure() throws InterruptedException {
		AiAnalysisService analysisService = mock(AiAnalysisService.class);
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondFinished = new CountDownLatch(1);
		List<Long> analyzedMessageIds = new CopyOnWriteArrayList<>();
		doAnswer(invocation -> {
			Long messageId = invocation.getArgument(1);
			analyzedMessageIds.add(messageId);
			if (messageId == 1L) {
				firstStarted.countDown();
				assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
				throw new IllegalStateException("worker failed");
			}
			secondFinished.countDown();
			return null;
		}).when(analysisService).analyze(org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong());
		AiAnalysisCoordinator coordinator = new AiAnalysisCoordinator(analysisService, executor);

		coordinator.submit(new MessageCreatedEvent(1L, 10L));
		assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
		coordinator.submit(new MessageCreatedEvent(2L, 10L));
		releaseFirst.countDown();

		assertTrue(secondFinished.await(5, TimeUnit.SECONDS));
		assertEquals(List.of(1L, 2L), analyzedMessageIds);
	}
}
