package musinsa.pay.point_server.common.generator;

import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.domain.idempotency.PointKeySequence;
import musinsa.pay.point_server.persistence.idempotency.PointKeySequenceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 포인트 거래 고유키(point_key) 생성기
 * - 18자 고유키 발급
 * - 형식: yyMMdd(6) + base36 random(3) + daily sequence(9)
 * - 일자별 시퀀스는 별도 테이블로 관리하여 동시성 이슈 해결
 */
@Component
@RequiredArgsConstructor
public class PointKeyGenerator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String BASE36_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int RAND_LENGTH = 3;
    private static final int SEQ_LENGTH = 9;
    private static final String SEQ_FORMAT = "%0" + SEQ_LENGTH + "d";

    private final PointKeySequenceRepository sequenceRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 18자 point_key 발급
     * yyMMdd(6) + base36 random(3) + daily sequence(9)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generate() {
        String today = LocalDate.now(KST).format(DATE_FORMAT);
        long seq = nextSequence(today);
        return today + randomBase36() + String.format(SEQ_FORMAT, seq);
    }

    private long nextSequence(String today) {
        return sequenceRepository.findByIdForUpdate(today)
            .map(PointKeySequence::getAndIncrement)
            .orElseGet(() -> initialSequence(today));
    }

    private long initialSequence(String today) {
        // 처음 호출: 새 row insert. UNIQUE 위반 시 (race) 재조회.
        try {
            PointKeySequence sequence = new PointKeySequence(today);
            Long issuedValue = sequence.getAndIncrement();
            sequenceRepository.saveAndFlush(sequence);
            return issuedValue;
        } catch (DataIntegrityViolationException e) {
            // 동시 insert 경합 발생 시 재조회
            return sequenceRepository.findByIdForUpdate(today)
                .orElseThrow(() -> new IllegalStateException("Sequence 초기화 실패: " + today))
                .getAndIncrement();
        }
    }

    private String randomBase36() {
        StringBuilder sb = new StringBuilder(RAND_LENGTH);
        for (int i = 0; i < RAND_LENGTH; i++) {
            sb.append(BASE36_ALPHABET.charAt(secureRandom.nextInt(BASE36_ALPHABET.length())));
        }
        return sb.toString();
    }
}
