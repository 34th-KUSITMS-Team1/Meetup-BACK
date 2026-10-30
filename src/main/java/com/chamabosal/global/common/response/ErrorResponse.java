package com.chamabosal.global.common.response;

import com.chamabosal.global.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ErrorResponse(
        boolean success,
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String field
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(false, errorCode.getCode(), message, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String field) {
        return new ErrorResponse(false, errorCode.getCode(), message, field);
    }
}
