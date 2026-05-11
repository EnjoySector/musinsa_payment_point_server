package musinsa.pay.point_server.api.admin;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.admin.PointAdminService;
import musinsa.pay.point_server.application.admin.dto.PointPolicyResponse;
import musinsa.pay.point_server.application.admin.dto.PointUserPolicyResponse;
import musinsa.pay.point_server.application.admin.dto.UpdatePointPolicyRequest;
import musinsa.pay.point_server.application.admin.dto.UpdatePointUserPolicyRequest;
import musinsa.pay.point_server.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 포인트 정책 관리 API
 * - 포인트 적립/만료 정책 조회 및 수정
 * - 사용자별 보유 한도 정책 조회 및 수정
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class PointPolicyController {

    private final PointAdminService pointAdminService;

    @GetMapping("/point-policies")
    public ResponseEntity<ApiResponse<List<PointPolicyResponse>>> pointPolicies() {
        List<PointPolicyResponse> response = pointAdminService.getPointPolicies();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/point-policies/{policyId}")
    public ResponseEntity<ApiResponse<PointPolicyResponse>> updatePointPolicy(
        @PathVariable Long policyId,
        @Valid @RequestBody UpdatePointPolicyRequest request
    ) {
        PointPolicyResponse response = pointAdminService.updatePointPolicy(policyId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/point-user-policies")
    public ResponseEntity<ApiResponse<List<PointUserPolicyResponse>>> pointUserPolicies() {
        List<PointUserPolicyResponse> response = pointAdminService.getPointUserPolicies();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/point-user-policies/{policyId}")
    public ResponseEntity<ApiResponse<PointUserPolicyResponse>> updatePointUserPolicy(
        @PathVariable Long policyId,
        @Valid @RequestBody UpdatePointUserPolicyRequest request
    ) {
        PointUserPolicyResponse response = pointAdminService.updatePointUserPolicy(policyId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
