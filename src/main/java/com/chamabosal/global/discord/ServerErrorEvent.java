package com.chamabosal.global.discord;

import java.time.Instant;
import java.util.Arrays;

/**
 * 5xx 에러 발생 시 발행되는 이벤트.
 * 요청·예외 객체는 요청이 끝난 뒤 다른 스레드에서 읽으면 안전하지 않아, 발행 시점에 알림에 필요한 값만 문자열로 복사해 담는다.
 */
public record ServerErrorEvent(
        String method,
        String uri,
        int status,
        String errorCode,
        String exceptionClass,
        String message,
        String location,
        Instant occurredAt
) {

    private static final String APP_PACKAGE = "com.chamabosal";

    public static ServerErrorEvent of(String method, String uri, int status, String errorCode, Exception e, Instant occurredAt) {
        return new ServerErrorEvent(
                method, uri, status, errorCode,
                e.getClass().getName(), e.getMessage(), location(e), occurredAt);
    }

    /** 스택 맨 위는 JDK·Spring 내부인 경우가 많아, 우리 코드에서 처음 등장하는 위치를 우선한다. */
    private static String location(Exception e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace.length == 0) {
            return null;
        }
        StackTraceElement frame = Arrays.stream(stackTrace)
                .filter(element -> element.getClassName().startsWith(APP_PACKAGE))
                .findFirst()
                .orElse(stackTrace[0]);
        return frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
    }
}
