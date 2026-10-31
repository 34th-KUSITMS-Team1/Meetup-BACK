package com.chamabosal.global.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DiscordErrorAlertListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-31T00:00:00Z");

    private final ServerErrorEvent event = eventOf(503, new IllegalStateException("boom"));
    private final DiscordErrorAlertListener noWebhookListener = new DiscordErrorAlertListener("");

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 웹훅_URL이_없으면_전송하지_않는다() {
        assertThatCode(() -> noWebhookListener.handle(event)).doesNotThrowAnyException();
    }

    @Test
    void 전송에_실패해도_예외를_던지지_않는다() {
        DiscordErrorAlertListener listener = new DiscordErrorAlertListener("http://localhost:1");

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
    }

    @Test
    void 네트워크_오류는_한_번_더_시도한다() throws IOException {
        // 응답 없이 연결을 끊어 클라이언트 쪽에서 I/O 오류가 나게 한다.
        AtomicInteger requests = startServer(HttpExchange::close);

        new DiscordErrorAlertListener(serverUrl()).handle(event);

        assertThat(requests).hasValue(2);
    }

    @Test
    void 응답_에러는_다시_시도하지_않는다() throws IOException {
        AtomicInteger requests = startServer(exchange -> respond(exchange, 400));

        new DiscordErrorAlertListener(serverUrl()).handle(event);

        assertThat(requests).hasValue(1);
    }

    @Test
    void 알림_본문은_에러_메시지_요청_코드_상태_위치_시간_순으로_담긴다() {
        Map<String, Object> embed = firstEmbed(noWebhookListener.toPayload(event));

        assertThat(embed.get("title")).isEqualTo("🚨 503 Service Unavailable");
        assertThat(description(embed)).isEqualTo("""
                **에러 메시지**
                ```
                IllegalStateException: boom
                ```
                **요청** : `GET /test`
                **코드/상태** : `COMMON_999 / 503`
                **위치** : `%s`
                **시간** : `2026-10-31 09:00:00`""".formatted(event.location()));
    }

    @Test
    void 위치는_우리_코드에서_처음_등장하는_곳을_가리킨다() {
        assertThat(event.location()).startsWith(DiscordErrorAlertListenerTest.class.getName());
    }

    @Test
    void 메시지가_비어_있으면_예외_클래스명만_보여준다() {
        ServerErrorEvent emptyMessageEvent = eventOf(500, new IllegalStateException(""));

        assertThat(description(firstEmbed(noWebhookListener.toPayload(emptyMessageEvent))))
                .startsWith("**에러 메시지**\n```\nIllegalStateException\n```");
    }

    @Test
    void 긴_메시지도_embed_설명_4096자_제한을_넘지_않는다() {
        ServerErrorEvent longMessageEvent = eventOf(500, new IllegalStateException("x".repeat(10_000)));

        assertThat(description(firstEmbed(noWebhookListener.toPayload(longMessageEvent)))).hasSizeLessThanOrEqualTo(4096);
    }

    private static ServerErrorEvent eventOf(int status, Exception e) {
        return ServerErrorEvent.of("GET", "/test", status, "COMMON_999", e, OCCURRED_AT);
    }

    private AtomicInteger startServer(Consumer<HttpExchange> handler) throws IOException {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            handler.accept(exchange);
        });
        server.start();
        return requests;
    }

    private String serverUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/webhook";
    }

    private static void respond(HttpExchange exchange, int status) {
        try {
            exchange.sendResponseHeaders(status, -1);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            exchange.close();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstEmbed(Map<String, Object> payload) {
        return ((List<Map<String, Object>>) payload.get("embeds")).getFirst();
    }

    private String description(Map<String, Object> embed) {
        return (String) embed.get("description");
    }
}
