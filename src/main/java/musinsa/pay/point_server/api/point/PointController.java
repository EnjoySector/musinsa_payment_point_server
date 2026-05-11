package musinsa.pay.point_server.api.point;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.earn.EarnCancelService;
import musinsa.pay.point_server.application.earn.EarnPointService;
import musinsa.pay.point_server.application.earn.dto.EarnCancelRequest;
import musinsa.pay.point_server.application.earn.dto.EarnCancelResponse;
import musinsa.pay.point_server.application.earn.dto.EarnPointRequest;
import musinsa.pay.point_server.application.earn.dto.EarnPointResponse;
import musinsa.pay.point_server.application.usage.UseCancelService;
import musinsa.pay.point_server.application.usage.UsePointService;
import musinsa.pay.point_server.application.usage.dto.UseCancelRequest;
import musinsa.pay.point_server.application.usage.dto.UseCancelResponse;
import musinsa.pay.point_server.application.usage.dto.UsePointRequest;
import musinsa.pay.point_server.application.usage.dto.UsePointResponse;
import musinsa.pay.point_server.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 적립/사용 API
 * - 적립: POST /api/v1/points/earn
 * - 적립 취소: POST /api/v1/points/earn/cancel
 * - 사용: POST /api/v1/points/use
 * - 사용 취소: POST /api/v1/points/use/cancel
 */
@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final EarnPointService earnPointService;
    private final EarnCancelService earnCancelService;
    private final UsePointService usePointService;
    private final UseCancelService useCancelService;

    @PostMapping("/earn")
    public ResponseEntity<ApiResponse<EarnPointResponse>> earn(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody EarnPointRequest request
    ) {
        EarnPointResponse response = earnPointService.earn(idempotencyKey, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/earn/cancel")
    public ResponseEntity<ApiResponse<EarnCancelResponse>> cancelEarn(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody EarnCancelRequest request
    ) {
        EarnCancelResponse response = earnCancelService.cancel(idempotencyKey, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/use")
    public ResponseEntity<ApiResponse<UsePointResponse>> use(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody UsePointRequest request
    ) {
        UsePointResponse response = usePointService.use(idempotencyKey, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/use/cancel")
    public ResponseEntity<ApiResponse<UseCancelResponse>> cancelUse(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody UseCancelRequest request
    ) {
        UseCancelResponse response = useCancelService.cancel(idempotencyKey, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
