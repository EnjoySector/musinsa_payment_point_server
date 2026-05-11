package musinsa.pay.point_server.api.admin;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.admin.PointAdminService;
import musinsa.pay.point_server.application.admin.dto.ManualEarnRequest;
import musinsa.pay.point_server.application.earn.dto.EarnPointResponse;
import musinsa.pay.point_server.application.expire.ExpirePointService;
import musinsa.pay.point_server.application.expire.dto.ExpirePointResponse;
import musinsa.pay.point_server.application.point.PointInfoService;
import musinsa.pay.point_server.application.point.dto.PointEarnSummaryResponse;
import musinsa.pay.point_server.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 포인트 API
 * - 수기 지급: 관리자 요청으로 특정 계정에 포인트 지급
 * - 수기 지급 내역 조회: 계정의 MANUAL 적립 내역 조회
 * - 만료 처리: 만료된 포인트를 EXPIRE 거래로 정리
 *
 * 인증/인가는 과제 범위에서 제외되어 있어 요청의 adminId를 관리자 행위자 식별자로 기록합니다.
 */
@RestController
@RequestMapping("/api/v1/admin/accounts/{accountId}/points")
@RequiredArgsConstructor
public class AdminPointController {

    private final PointAdminService pointAdminService;
    private final PointInfoService pointInfoService;
    private final ExpirePointService expirePointService;

    @PostMapping("/manual-earns")
    public ResponseEntity<ApiResponse<EarnPointResponse>> manualEarn(
        @PathVariable Long accountId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ManualEarnRequest request
    ) {
        EarnPointResponse response = pointAdminService.manualEarn(accountId, idempotencyKey, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/manual-earns")
    public ResponseEntity<ApiResponse<List<PointEarnSummaryResponse>>> manualEarns(@PathVariable Long accountId) {
        List<PointEarnSummaryResponse> response = pointInfoService.getManualEarns(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/expire")
    public ResponseEntity<ApiResponse<ExpirePointResponse>> expire(@PathVariable Long accountId) {
        ExpirePointResponse response = expirePointService.expire(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
