package com.chamabosal.global.common;

import com.chamabosal.global.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 상수별 본문이 있는(abstract로 컴파일되는) enum도 에러코드 페이지에 수집되는지 확인하기 위한 테스트용 enum. */
enum TestErrorCode implements ErrorCode {

    TEST_ERROR {
        @Override
        public String getMessage() {
            return "테스트용 에러입니다.";
        }
    };

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String getCode() {
        return "TEST_001";
    }
}
