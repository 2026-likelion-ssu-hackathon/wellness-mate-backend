package com.suspiciouslions.backend.domain.ai.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai-worker")
public record AiWorkerProperties(URI baseUrl, Duration timeout) {
}
