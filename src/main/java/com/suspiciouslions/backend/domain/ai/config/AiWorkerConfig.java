package com.suspiciouslions.backend.domain.ai.config;

import java.time.Clock;
import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@EnableAsync
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiWorkerProperties.class)
public class AiWorkerConfig {

	@Bean
	RestClient aiWorkerRestClient(AiWorkerProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.timeout());
		requestFactory.setReadTimeout(properties.timeout());
		return RestClient.builder()
				.baseUrl(properties.baseUrl().toString())
				.requestFactory(requestFactory)
				.build();
	}

	@Bean(name = "aiWorkerExecutor")
	Executor aiWorkerExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("ai-worker-");
		return executor;
	}

	@Bean
	Clock aiWorkerClock() {
		return Clock.systemUTC();
	}
}
