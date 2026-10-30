package com.chamabosal.global.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인별 에러코드 enum이 구현하는 인터페이스.
 * code는 DOMAIN_nnn 형식으로 작성한다. (예: COMMON_001, MEETUP_001)
 */
public interface ErrorCode {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}
