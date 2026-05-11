package musinsa.pay.point_server.support;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * 테스트 저장 완료 엔티티 ID 주입 도우미
 */
public final class EntityTestSupport {

    private EntityTestSupport() {
    }

    public static <T> T persisted(
        T entity,
        Long id
    ) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
