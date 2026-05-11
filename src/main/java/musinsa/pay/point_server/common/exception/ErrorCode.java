package musinsa.pay.point_server.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== Common (4xx) =====
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청이 올바르지 않습니다"),
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "입력 값이 올바르지 않습니다"),
    MISSING_REQUIRED_HEADER(HttpStatus.BAD_REQUEST, "MISSING_REQUIRED_HEADER", "헤더가 올바르지 않습니다."),
    INVALID_STATE(HttpStatus.BAD_REQUEST, "INVALID_STATE", "올바른 상태가 아닙니다."),
    API_NOT_FOUND(HttpStatus.NOT_FOUND, "API_NOT_FOUND", "요청한 API를 찾을 수 없습니다."),

    // ===== Idempotency =====
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 헤더가 필요합니다"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "동일 키로 다른 요청 본문이 들어왔습니다"),
    IDEMPOTENCY_PROCESSING(HttpStatus.CONFLICT, "IDEMPOTENCY_PROCESSING", "이전 요청이 처리 중입니다"),

    // ===== User =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다"),

    // ===== Account =====
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다"),
    ACCOUNT_BLOCKED(HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED", "차단된 계정입니다"),
    ACCOUNT_DELETED(HttpStatus.GONE, "ACCOUNT_DELETED", "삭제된 계정입니다"),

    // ===== Point =====
    POINT_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_POLICY_NOT_FOUND", "포인트 정책을 찾을 수 없습니다"),
    POINT_USER_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_USER_POLICY_NOT_FOUND", "사용자 포인트 정책을 찾을 수 없습니다"),
    POINT_POLICY_INACTIVE(HttpStatus.BAD_REQUEST, "POINT_POLICY_INACTIVE", "비활성화된 포인트 정책입니다"),
    POINT_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "POINT_AMOUNT_INVALID", "포인트 금액이 올바르지 않습니다"),
    POINT_EARN_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "POINT_EARN_AMOUNT_EXCEEDED", "1회 적립 가능 포인트를 초과했습니다"),
    POINT_BALANCE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "POINT_BALANCE_LIMIT_EXCEEDED", "보유 가능 포인트 한도를 초과했습니다"),
    POINT_EXPIRE_DAYS_INVALID(HttpStatus.BAD_REQUEST, "POINT_EXPIRE_DAYS_INVALID", "포인트 만료일이 올바르지 않습니다"),
    POINT_EARN_TYPE_INVALID(HttpStatus.BAD_REQUEST, "POINT_EARN_TYPE_INVALID", "적립 유형이 올바르지 않습니다"),
    POINT_MANUAL_EARN_FORBIDDEN(HttpStatus.FORBIDDEN, "POINT_MANUAL_EARN_FORBIDDEN", "관리자만 수기 적립할 수 있습니다"),
    POINT_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_TRANSACTION_NOT_FOUND", "포인트 거래를 찾을 수 없습니다"),
    POINT_EARN_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_EARN_NOT_FOUND", "포인트 적립 내역을 찾을 수 없습니다"),
    POINT_EARN_CANCEL_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_EARN_CANCEL_NOT_FOUND", "포인트 적립취소 내역을 찾을 수 없습니다"),
    POINT_EARN_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "POINT_EARN_CANCEL_NOT_ALLOWED", "포인트 적립취소가 불가능한 상태입니다"),
    POINT_EARN_ALREADY_USED(HttpStatus.BAD_REQUEST, "POINT_EARN_ALREADY_USED", "이미 사용된 적립 포인트입니다"),
    POINT_EARN_EXPIRED(HttpStatus.BAD_REQUEST, "POINT_EARN_EXPIRED", "이미 만료된 적립 포인트입니다"),
    POINT_EARN_ALREADY_CANCELED(HttpStatus.BAD_REQUEST, "POINT_EARN_ALREADY_CANCELED", "이미 취소된 적립 포인트입니다"),
    POINT_EARN_CANCEL_BALANCE_INSUFFICIENT(HttpStatus.BAD_REQUEST, "POINT_EARN_CANCEL_BALANCE_INSUFFICIENT", "적립취소 가능한 잔액이 부족합니다"),
    POINT_LEDGER_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_LEDGER_NOT_FOUND", "포인트 원장을 찾을 수 없습니다"),
    POINT_BALANCE_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "POINT_BALANCE_NOT_ENOUGH", "사용 가능한 포인트 잔액이 부족합니다"),
    POINT_USAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_USAGE_NOT_FOUND", "포인트 사용 내역을 찾을 수 없습니다"),
    POINT_USAGE_DUPLICATE_ORDER(HttpStatus.CONFLICT, "POINT_USAGE_DUPLICATE_ORDER", "이미 포인트를 사용한 주문입니다"),
    POINT_USAGE_CANCEL_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_USAGE_CANCEL_NOT_FOUND", "포인트 사용취소 내역을 찾을 수 없습니다"),
    POINT_USAGE_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "POINT_USAGE_CANCEL_NOT_ALLOWED", "포인트 사용취소가 불가능한 상태입니다"),
    POINT_USAGE_ALREADY_CANCELED(HttpStatus.BAD_REQUEST, "POINT_USAGE_ALREADY_CANCELED", "이미 전체 취소된 포인트 사용입니다"),
    POINT_USAGE_CANCEL_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "POINT_USAGE_CANCEL_AMOUNT_EXCEEDED", "사용취소 가능 금액을 초과했습니다"),
    POINT_USAGE_CANCEL_ALLOCATION_INSUFFICIENT(HttpStatus.BAD_REQUEST, "POINT_USAGE_CANCEL_ALLOCATION_INSUFFICIENT", "사용취소 가능한 배분 금액이 부족합니다"),

    // ===== System (5xx) =====
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "데이터 무결성 위반"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "내부 서버 오류가 발생했습니다"),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
