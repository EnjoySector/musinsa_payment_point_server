package musinsa.pay.point_server.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import musinsa.pay.point_server.common.response.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 도메인 규칙 위반 예외 처리.
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(BaseException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[Exception] {} - {}", errorCode.getCode(), e.getMessage());
        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(ApiResponse.error(errorCode.getCode(), e.getMessage()));
    }

    /**
     * @Valid RequestBody 검증 실패.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.warn("[Validation] {}", detail);
        return invalidArgument(detail);
    }

    /**
     * Header, PathVariable, RequestParam 검증 실패.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String detail = e.getAllErrors().stream()
            .map(error -> error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.warn("[Method Validation] {}", detail);
        return invalidArgument(detail);
    }

    /**
     * Method-level Bean Validation 검증 실패.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining(", "));
        log.warn("[Constraint Violation] {}", detail);
        return invalidArgument(detail);
    }

    /**
     * 도메인 메서드 인자 검증 실패.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        String message = e.getMessage() != null
            ? e.getMessage()
            : ErrorCode.INVALID_ARGUMENT.getMessage();
        log.warn("[Illegal Argument] {}", message);
        return invalidArgument(message);
    }

    /**
     * 도메인 객체 상태 오류.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        String message = e.getMessage() != null
            ? e.getMessage()
            : ErrorCode.INVALID_STATE.getMessage();
        log.warn("[Illegal State] {}", message);
        return ResponseEntity
            .status(ErrorCode.INVALID_STATE.getHttpStatus())
            .body(ApiResponse.error(ErrorCode.INVALID_STATE.getCode(), message));
    }

    /**
     * 필수 헤더 누락.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        String message = "필수 헤더가 누락되었습니다: " + e.getHeaderName();
        log.warn("[Missing Header] {}", e.getHeaderName());
        return ResponseEntity
            .status(ErrorCode.MISSING_REQUIRED_HEADER.getHttpStatus())
            .body(ApiResponse.error(ErrorCode.MISSING_REQUIRED_HEADER.getCode(), message));
    }

    /**
     * JSON 파싱 실패 또는 요청 본문 형식 오류.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[Not Readable] {}", e.getMessage());
        return ResponseEntity
            .status(ErrorCode.INVALID_REQUEST.getHttpStatus())
            .body(ApiResponse.error(
                ErrorCode.INVALID_REQUEST.getCode(),
                "요청 본문 형식이 올바르지 않습니다."));
    }

    /**
     * PathVariable, RequestParam 타입 불일치.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("[Type Mismatch] {} = {}", e.getName(), e.getValue());
        return invalidArgument("파라미터 타입이 올바르지 않습니다: " + e.getName());
    }

    /**
     * DB 제약 위반.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("[Data Integrity] {}", e.getMessage());
        return ResponseEntity
            .status(ErrorCode.DATA_INTEGRITY_VIOLATION.getHttpStatus())
            .body(ApiResponse.error(
                ErrorCode.DATA_INTEGRITY_VIOLATION.getCode(),
                ErrorCode.DATA_INTEGRITY_VIOLATION.getMessage()));
    }

    /**
     * 매핑되지 않은 API 또는 정적 리소스 요청.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoResource(Exception e) {
        log.warn("[Not Found] {}", e.getMessage());
        return ResponseEntity
            .status(ErrorCode.API_NOT_FOUND.getHttpStatus())
            .body(ApiResponse.error(
                ErrorCode.API_NOT_FOUND.getCode(),
                ErrorCode.API_NOT_FOUND.getMessage()));
    }

    /**
     * 예상하지 못한 서버 오류.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[Unhandled]", e);
        return ResponseEntity
            .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
            .body(ApiResponse.error(
                ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> invalidArgument(String message) {
        return ResponseEntity
            .status(ErrorCode.INVALID_ARGUMENT.getHttpStatus())
            .body(ApiResponse.error(ErrorCode.INVALID_ARGUMENT.getCode(), message));
    }
}
