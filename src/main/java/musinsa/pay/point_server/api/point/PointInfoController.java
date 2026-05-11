package musinsa.pay.point_server.api.point;

import java.util.List;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.point.PointInfoService;
import musinsa.pay.point_server.application.point.dto.EarnUsageTraceResponse;
import musinsa.pay.point_server.application.point.dto.PointSummaryResponse;
import musinsa.pay.point_server.application.point.dto.PointTransactionResponse;
import musinsa.pay.point_server.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 정보 조회 API
 * - 포인트 잔액 및 요약 정보 조회
 * - 포인트 거래 내역 조회 (적립/사용/취소)
 * - 적립 포인트의 사용 내역 추적 (어떤 거래에서 사용되었는지)
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/points")
@RequiredArgsConstructor
public class PointInfoController {

    private final PointInfoService pointInfoService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<PointSummaryResponse>> summary(@PathVariable Long accountId) {
        PointSummaryResponse response = pointInfoService.getSummary(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<PointTransactionResponse>>> transactions(
        @PathVariable Long accountId,
        @RequestParam(defaultValue = "20") int limit
    ) {
        List<PointTransactionResponse> response = pointInfoService.getTransactions(accountId, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/earns/{earnPointKey}/usages")
    public ResponseEntity<ApiResponse<EarnUsageTraceResponse>> earnUsages(
        @PathVariable Long accountId,
        @PathVariable String earnPointKey
    ) {
        EarnUsageTraceResponse response = pointInfoService.traceEarnUsages(accountId, earnPointKey);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
