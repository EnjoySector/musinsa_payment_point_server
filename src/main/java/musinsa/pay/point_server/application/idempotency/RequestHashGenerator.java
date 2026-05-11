package musinsa.pay.point_server.application.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 요청 본문을 해시로 변환하는 유틸리티 클래스
 * - JSON 직렬화 시 키 정렬 및 날짜 형식 통일
 * - SHA-256 해시 생성
 * - 해시 충돌 방지 및 멱등성 보장
 */
@Component
public class RequestHashGenerator {

    private final ObjectMapper hashObjectMapper;

    public RequestHashGenerator(ObjectMapper objectMapper) {
        this.hashObjectMapper = objectMapper.copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.hashObjectMapper.setConfig(
            this.hashObjectMapper.getSerializationConfig()
                .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        );
    }

    public String generate(Object body) {
        try {
            String canonicalJson = hashObjectMapper.writeValueAsString(body);
            byte[] payload = canonicalJson.getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException e) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "멱등성 요청 본문을 해시로 변환할 수 없습니다.");
        } catch (NoSuchAlgorithmException e) {
            throw new BaseException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
