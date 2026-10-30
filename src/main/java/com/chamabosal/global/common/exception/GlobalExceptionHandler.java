package com.chamabosal.global.common.exception;

import com.chamabosal.global.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error("BaseException: {}", e.getMessage(), e);
        } else {
            log.warn("BaseException: {}", e.getMessage());
        }
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e) {
        return toResponse(BaseErrorCode.ENDPOINT_NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String field = fieldError != null ? fieldError.getField() : null;
        String message = fieldError != null ? fieldError.getDefaultMessage() : BaseErrorCode.INVALID_REQUEST.getMessage();
        log.warn("MethodArgumentNotValidException: {} {}", field, message);
        return toResponse(BaseErrorCode.INVALID_REQUEST, message, field);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        ConstraintViolation<?> violation = e.getConstraintViolations().stream().findFirst().orElse(null);
        String field = violation != null ? extractLeafName(violation.getPropertyPath()) : null;
        String message = violation != null ? violation.getMessage() : BaseErrorCode.INVALID_REQUEST.getMessage();
        log.warn("ConstraintViolationException: {} {}", field, message);
        return toResponse(BaseErrorCode.INVALID_REQUEST, message, field);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        String field = e.getParameterValidationResults().stream()
                .map(result -> result.getMethodParameter().getParameterName())
                .findFirst()
                .orElse(null);
        String message = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse(BaseErrorCode.INVALID_REQUEST.getMessage());
        log.warn("HandlerMethodValidationException: {} {}", field, message);
        return toResponse(BaseErrorCode.INVALID_REQUEST, message, field);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException: {}", e.getMessage());
        return toResponse(BaseErrorCode.INVALID_JSON_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("MissingServletRequestParameterException: {}", e.getMessage());
        return toResponse(BaseErrorCode.MISSING_PARAMETER, BaseErrorCode.MISSING_PARAMETER.getMessage(), e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException: {}", e.getMessage());
        return toResponse(BaseErrorCode.TYPE_MISMATCH, BaseErrorCode.TYPE_MISMATCH.getMessage(), e.getName());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return toResponse(BaseErrorCode.METHOD_NOT_ALLOWED, e);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        return toResponse(BaseErrorCode.UNSUPPORTED_MEDIA_TYPE, e);
    }

    /**
     * 위에서 개별 처리하지 않은 Spring MVC 예외는 원래 상태코드와 헤더를 유지한 채
     * 4xx면 UNPROCESSABLE_REQUEST, 5xx면 UNHANDLED_ERROR로 변환한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springErrorResponse) {
            if (springErrorResponse.getStatusCode().is4xxClientError()) {
                log.warn("UnprocessableRequest: {}", e.getMessage());
                return toResponse(BaseErrorCode.UNPROCESSABLE_REQUEST, springErrorResponse);
            }
            log.error("UnhandledException: {}", e.getMessage(), e);
            return toResponse(BaseErrorCode.UNHANDLED_ERROR, springErrorResponse);
        }
        log.error("UnhandledException: {}", e.getMessage(), e);
        return toResponse(BaseErrorCode.UNHANDLED_ERROR);
    }

    /** 메서드 검증의 경로(createItem.request.title)에서 마지막 필드명(title)만 꺼낸다. */
    private String extractLeafName(Path propertyPath) {
        String leafName = null;
        for (Path.Node node : propertyPath) {
            leafName = node.getName();
        }
        return leafName;
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode));
    }

    /** Spring 예외의 상태코드와 헤더(Allow, Accept 등)를 그대로 유지한다. */
    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, org.springframework.web.ErrorResponse springErrorResponse) {
        return ResponseEntity
                .status(springErrorResponse.getStatusCode())
                .headers(springErrorResponse.getHeaders())
                .body(ErrorResponse.of(errorCode));
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, String message, String field) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, message, field));
    }
}
