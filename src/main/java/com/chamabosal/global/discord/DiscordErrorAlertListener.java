package com.chamabosal.global.discord;

import com.chamabosal.global.config.AsyncConfig;
import com.chamabosal.global.config.ClockConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DiscordErrorAlertListener {

    private static final int RED = 0xE74C3C;
    // Discord는 embed 설명이 4096자를 넘으면 요청을 거절한다. 스택트레이스는 서버 로그에서 확인한다.
    private static final int MESSAGE_LIMIT = 1000;
    private static final int VALUE_LIMIT = 256;
    private static final String EMPTY_VALUE = "N/A";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    // 연결 실패·타임아웃 같은 일시적 네트워크 오류만 한 번 더 시도한다. 4xx 등은 다시 보내도 실패한다.
    private static final int MAX_ATTEMPTS = 2;
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient restClient;
    private final String webhookUrl;

    public DiscordErrorAlertListener(@Value("${discord.webhook.error-url:}") String webhookUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.webhookUrl = webhookUrl;
    }

    /** 웹훅 URL이 없으면(로컬 등) 전송하지 않고, 전송 실패는 로그만 남겨 API 응답에 영향을 주지 않는다. */
    @Async(AsyncConfig.DISCORD_ALERT_EXECUTOR)
    @EventListener
    public void handle(ServerErrorEvent event) {
        if (webhookUrl.isBlank()) {
            return;
        }
        Map<String, Object> payload = toPayload(event);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                send(payload);
                return;
            } catch (ResourceAccessException e) {
                // 예외 메시지에는 토큰이 포함된 웹훅 URL이 담길 수 있어 타입만 남긴다.
                log.warn("Discord 에러 알림 전송 실패({}/{}): {}", attempt, MAX_ATTEMPTS, e.getClass().getSimpleName());
                if (attempt < MAX_ATTEMPTS && !sleepBeforeRetry()) {
                    return;
                }
            } catch (RestClientResponseException e) {
                log.warn("Discord 에러 알림 전송 실패: status={}", e.getStatusCode().value());
                return;
            } catch (Exception e) {
                log.warn("Discord 에러 알림 전송 실패: {}", e.getClass().getSimpleName());
                return;
            }
        }
    }

    Map<String, Object> toPayload(ServerErrorEvent event) {
        String description = String.format(
                "**에러 메시지**\n```\n%s\n```\n" +
                        "**요청** : `%s`\n" +
                        "**코드/상태** : `%s / %d`\n" +
                        "**위치** : `%s`\n" +
                        "**시간** : `%s`",
                errorMessage(event),
                value(event.method() + " " + event.uri()),
                value(event.errorCode()),
                event.status(),
                value(event.location()),
                event.occurredAt().atZone(ClockConfig.KST).format(TIME_FORMAT)
        );
        Map<String, Object> embed = Map.of(
                "title", "🚨 " + statusText(event.status()),
                "color", RED,
                "description", description
        );
        return Map.of("embeds", List.of(embed));
    }

    private void send(Map<String, Object> payload) {
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    /** 대기 중 인터럽트되면(종료 등) 재시도하지 않는다. */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 예외 클래스명(패키지 제외)과 메시지를 한 줄로 보여준다. 메시지가 없으면 클래스명만 남긴다. */
    private String errorMessage(ServerErrorEvent event) {
        String className = event.exceptionClass().substring(event.exceptionClass().lastIndexOf('.') + 1);
        String message = event.message();
        if (message == null || message.isBlank()) {
            return className;
        }
        return truncate(className + ": " + message, MESSAGE_LIMIT);
    }

    private String value(String value) {
        return value == null || value.isBlank() ? EMPTY_VALUE : truncate(value, VALUE_LIMIT);
    }

    private String statusText(int status) {
        HttpStatus httpStatus = HttpStatus.resolve(status);
        return httpStatus != null ? status + " " + httpStatus.getReasonPhrase() : String.valueOf(status);
    }

    private String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 3) + "...";
    }
}
