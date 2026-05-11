# 무료 포인트 시스템 API

무료 포인트의 적립, 적립취소, 사용, 사용취소를 처리하는 API 서버입니다.

포인트 도메인이 중심인 과제이므로 사용자 정보는 최소한의 식별 정보만 두었습니다. 
인증, 인가, 회원 상세 정보, 관리자 계정 관리는 구현 범위에서 제외했습니다. 
일반 사용자 API의 행위자는 계정 정보에서 내부 세팅하고, 
관리자 API는 `adminId`, 시스템 처리는 `SYSTEM` 식별자를 사용합니다.

### 개발 환경

- Java 21
- Spring Boot 3
- Spring Data JPA
- H2 Database
- Gradle

### 빌드 및 실행

```bash
./gradlew clean build
./gradlew bootRun
```

H2 Console:

```text
http://localhost:8080/h2-console
```

접속 정보:

```text
JDBC URL: jdbc:h2:mem:testdb
User Name: sa
Password: password
```

### 초기 데이터

초기 데이터는 `src/main/resources/data.sql`에 있습니다. 포인트 흐름을 바로 확인할 수 있도록 계정 3개와 정책 데이터를 미리 넣어두었습니다.

| 구분 | 값 | 설명 |
|---|---:|---|
| 기본 포인트 정책 | `point_policy.DEFAULT` | 1회 최대 적립 `100000`, 기본 만료일 `365일`, 최소 `1일`, 최대 `1824일` |
| 기본 사용자 정책 | `point_user_policy.DEFAULT` | 사용자별 최대 보유 가능 포인트 `1000000` |
| 낮은 한도 사용자 정책 | `point_user_policy.LOW_LIMIT` | 사용자별 최대 보유 가능 포인트 `1500` |
| 기본 테스트 계정 | `account_id = 1` | 정상 적립, 사용, 취소 흐름 확인용 |
| 한도 테스트 계정 | `account_id = 2` | 사용자별 보유 한도 초과 검증용 |
| 관리자/복합 테스트 계정 | `account_id = 3` | 수기 지급, 만료, 사용취소 흐름 확인용 |

초기 잔액은 세 계정 모두 `0`입니다. 거래, 적립, 사용 데이터는 API 호출로 생성되도록 비워두었습니다.

### 요구사항

| 요구사항 | 구현 방식 |
|---|---|
| 1회 적립 가능 금액 제어 | `point_policy.max_earn_amount`로 관리 |
| 사용자별 최대 보유 포인트 제어 | `point_user_policy.max_balance_amount`로 관리 |
| 적립 포인트의 주문 사용 추적 | `point_usage_allocation`에 적립별 사용 배분 저장 |
| 관리자 수기 지급 구분 | `point_earn.earn_type = MANUAL` |
| 포인트 만료일 | `point_earn.expires_at`에 적립 단위로 저장 |
| 적립취소 | 미사용 적립만 전체 취소 가능 |
| 포인트 사용 | 주문번호를 `point_usage.order_no`에 저장 |
| 사용 우선순위 | 수기 지급 우선, 이후 만료일 빠른 순 |
| 사용취소 | 전체/부분 취소 가능 |
| 만료 포인트 사용취소 | 원 적립 복구 대신 신규 적립 생성 |
| 멱등성 | `point_idempotency`로 중복 요청 방지 |
| 잔액 감사 | `point_transaction`, `point_ledger` 기록 |

### 처리 흐름

모든 포인트 변경 요청은 같은 패턴으로 처리합니다.

1. `point_balance`를 비관적 락으로 조회합니다.
2. `point_idempotency`로 같은 요청이 이미 처리됐는지 확인합니다.
3. 해당 계정의 만료 대상 적립을 먼저 정리합니다.
4. 정책과 상태를 검증한 뒤 실제 적립, 취소, 사용, 사용취소를 처리합니다.
5. `point_transaction`에는 거래 헤더를, `point_ledger`에는 실제 잔액 변화를 남깁니다.

락 기준 쿼리는 다음과 같습니다.

```sql
SELECT *
FROM point_balance
WHERE account_id = ?
FOR UPDATE;
```

Spring Data JPA를 사용하지만 잔액 정합성이 중요한 지점은 `PESSIMISTIC_WRITE`와 명시적인 update query로 제어했습니다.

### 식별자 키

포인트 거래에는 내부 DB 식별자와 외부 처리 기준 키를 분리해서 사용합니다.

| 구분 | 위치 | 용도 |
|---|---|---|
| `transactionId` | `point_transaction.id` | 내부 거래 PK, 원장/상세 테이블 조인 기준 |
| `pointKey` | `point_transaction.point_key` | API 응답과 취소 요청에서 사용하는 거래 비즈니스 키 |
| `Idempotency-Key` | 요청 Header | 같은 API 요청의 중복 처리를 막기 위한 클라이언트 요청 키 |

`pointKey`는 `PointKeyGenerator`에서 서버가 발급합니다. 형식은 `yyMMdd + random base36 3자리 + 일자별 sequence 9자리`입니다.

```text
예: 260511A3F000000001
```

일자별 sequence는 `point_key_sequence` 테이블에서 관리하며, 발급 시 해당 일자 row를 비관적 락으로 잠급니다. 또한 `point_transaction.point_key`에 UNIQUE 제약을 두어 DB 레벨에서도 중복 발급을 방지합니다.

취소 API는 내부 PK가 아니라 원 거래의 `pointKey`를 기준으로 요청합니다. 
예를 들어 적립취소는 적립 응답의 `pointKey`, 사용취소는 사용 응답의 `pointKey`를 사용합니다.

## 기능별 흐름

### 적립

- API: `POST /api/v1/points/earn`
- 주요 코드: `EarnPointService`, `EarnPointProcessor`, `EarnPointContextFactory`, `EarnPointValidator`
- 주요 테이블: `point_transaction`, `point_earn`, `point_balance`, `point_ledger`

`EarnPointService`가 요청을 멱등성 요청으로 변환하고, 공통 실행 흐름인 `PointCommandTemplate`을 호출합니다. 
실제 적립 생성은 `EarnPointProcessor`가 담당하며, 정책의 1회 적립 한도와 사용자 보유 한도를 검증한 뒤 `EARN` 거래와 적립 row를 생성합니다.

### 적립취소

- API: `POST /api/v1/points/earn/cancel`
- 주요 코드: `EarnCancelService`, `EarnCancelProcessor`, `EarnCancelContextFactory`, `EarnCancelValidator`
- 주요 테이블: `point_transaction`, `point_earn_cancel`, `point_earn`, `point_balance`, `point_ledger`

특정 적립 건의 전체 금액만 취소할 수 있습니다. 
이미 일부라도 사용됐거나 만료 또는 취소된 적립이면 거절합니다. 
성공 시 `EARN_CANCEL` 거래와 취소 상세를 저장하고 잔액을 차감합니다.

### 사용

- API: `POST /api/v1/points/use`
- 주요 코드: `UsePointService`, `UsePointProcessor`, `PointUsageAllocationCalculator`, `UsePointValidator`
- 주요 테이블: `point_transaction`, `point_usage`, `point_usage_allocation`, `point_earn`, `point_balance`, `point_ledger`

주문번호를 기준으로 포인트를 사용합니다. 
사용 가능한 적립을 `MANUAL` 우선, 만료일 빠른 순서로 배분하고, 
어떤 적립에서 얼마를 사용했는지 `point_usage_allocation`에 남깁니다.

### 사용취소

- API: `POST /api/v1/points/use/cancel`
- 주요 코드: `UseCancelService`, `UseCancelProcessor`, `PointUsageCancelAllocationCalculator`, `UseCancelValidator`
- 주요 테이블: `point_transaction`, `point_usage_cancel`, `point_usage_cancel_allocation`, `point_usage_allocation`, `point_earn`, `point_balance`, `point_ledger`

사용 금액 중 전체 또는 일부를 취소할 수 있습니다. 
원래 차감된 적립이 아직 만료되지 않았다면 원 적립으로 복구하고, 이미 만료됐다면 `USE_CANCEL_RESTORE` 유형의 신규 적립을 생성합니다.

## 적립별 사용 추적

포인트 사용 시 `point_usage_allocation`에 어떤 적립 건에서 얼마를 사용했는지 저장합니다.

예를 들어 다음 순서로 처리했다고 가정합니다.

1. A 적립: 1000원 적립
2. B 적립: 500원 적립
3. 주문번호 `A1234`에서 1200원 사용

이때 사용 헤더는 다음처럼 쌓입니다.

| table | id | order_no | usage_amount | cancelled_amount | status |
|---|---:|---|---:|---:|---|
| `point_usage` | 1 | `A1234` | 1200 | 0 | `USED` |

사용 배분은 적립 단위로 나뉩니다.

| table | usage_id | earn_id | allocation_seq | amount | cancelled_amount | status |
|---|---:|---:|---:|---:|---:|---|
| `point_usage_allocation` | 1 | A 적립 id | 1 | 1000 | 0 | `USED` |
| `point_usage_allocation` | 1 | B 적립 id | 2 | 200 | 0 | `USED` |

그래서 A 적립의 사용 이력을 조회하면 `A1234` 주문에서 1000원이 사용됐다는 것을 확인할 수 있습니다. 
금액 컬럼은 1원 단위 정수 금액으로 저장하므로 적립 포인트가 어떤 주문에서 사용됐는지 1원 단위까지 추적할 수 있습니다.

조회 API:

```http
GET /api/v1/accounts/{accountId}/points/earns/{earnPointKey}/usages
```

## 사용취소와 만료 포인트 예시

위 예시에서 A 적립이 만료된 뒤, 주문 `A1234`의 1200원 사용 중 1100원을 취소하면 다음처럼 처리합니다.

| 취소 대상 | 취소 금액 | 처리 방식 |
|---|---:|---|
| A 적립에서 사용된 금액 | 1000 | 원 적립이 만료됐으므로 신규 적립 생성 |
| B 적립에서 사용된 금액 | 100 | 아직 만료되지 않았으므로 B 적립으로 복구 |

이때 `point_usage_cancel_allocation.restore_type`은 각각 `NEW_EARN`, `ORIGINAL_EARN`으로 남습니다.


## 만료 처리

현재 구현은 관리자 만료 API와 포인트 변경 요청 시점의 만료 정리를 기준으로 동작합니다. 
운영 환경에서는 별도 scheduler 또는 batch job으로 만료 대상 포인트를 주기적으로 처리하도록 확장할 수 있습니다.



# API

모든 변경 API는 `Idempotency-Key` 헤더가 필요합니다. 
직접 호출할 때는 요청마다 임의의 고유 문자열을 넣으면 됩니다. 
같은 요청을 재시도할 때는 같은 key를 다시 사용합니다.

### 포인트 적립

```http
POST /api/v1/points/earn
Idempotency-Key: earn-001
Content-Type: application/json
```

```json
{
  "accountId": 1,
  "amount": 1000,
  "expireDays": 365,
  "earnType": "NORMAL",
  "reason": "order reward"
}
```

### 적립취소

```http
POST /api/v1/points/earn/cancel
Idempotency-Key: earn-cancel-001
Content-Type: application/json
```

```json
{
  "accountId": 1,
  "earnPointKey": "적립 응답의 pointKey",
  "reason": "earn cancel"
}
```

### 포인트 사용

```http
POST /api/v1/points/use
Idempotency-Key: use-001
Content-Type: application/json
```

```json
{
  "accountId": 1,
  "orderNo": "A1234",
  "amount": 1200,
  "reason": "order payment"
}
```

### 사용취소

```http
POST /api/v1/points/use/cancel
Idempotency-Key: use-cancel-001
Content-Type: application/json
```

```json
{
  "accountId": 1,
  "usePointKey": "사용 응답의 pointKey",
  "cancelAmount": 1100,
  "reason": "order cancel"
}
```

### 관리자 수기 지급

```http
POST /api/v1/admin/accounts/3/points/manual-earns
Idempotency-Key: manual-earn-001
Content-Type: application/json
```

```json
{
  "amount": 1000,
  "expireDays": 365,
  "adminId": "admin-1",
  "reason": "CS manual reward"
}
```

## 조회 API

```http
GET /api/v1/accounts/{accountId}/points/summary
GET /api/v1/accounts/{accountId}/points/transactions?limit=20
GET /api/v1/accounts/{accountId}/points/earns/{earnPointKey}/usages
```

## 관리자 API

```http
POST /api/v1/admin/accounts/{accountId}/points/manual-earns
GET  /api/v1/admin/accounts/{accountId}/points/manual-earns
POST /api/v1/admin/accounts/{accountId}/points/expire

GET   /api/v1/admin/point-policies
PATCH /api/v1/admin/point-policies/{policyId}
GET   /api/v1/admin/point-user-policies
PATCH /api/v1/admin/point-user-policies/{policyId}
```

## 테스트

```bash
./gradlew test
```

테스트는 기능별로 나누어 정상 흐름과 예외 흐름을 같이 확인합니다.

| 구분 | 주요 검증 |
|---|---|
| 적립 | 정상 적립, 1회 적립 한도 초과, 사용자별 보유 한도 초과, 사용자 정책별 보유 한도 차이, 일반 API의 수기 지급 거절, 만료일 범위 검증 |
| 적립취소 | 정상 취소, 이미 사용된 적립 취소 거절, 이미 만료된 적립 취소 거절, 이미 취소된 적립 재취소 거절, 멱등성 재호출 |
| 사용 | 수기 지급 우선 사용, 만료일 빠른 순 사용, 잔액 부족 거절, 중복 주문번호 거절, 멱등성 재호출 |
| 사용취소 | 부분 취소, 전체 취소, 취소 가능 금액 초과 거절, 만료된 원 적립의 신규 적립 처리, 원 적립 복구 처리 |
| 만료 | 만료 대상 적립 정리, `EXPIRE` 거래 생성, 만료 원장 생성, 만료 대상이 없을 때 무변경 |
| 멱등성 | 같은 key 재호출, 같은 key의 다른 body 충돌, 처리 중 요청 거절, 빈 key 거절 |

## ERD

ERD 파일은 src/main/resource/erd.svg 에 포함했습니다.


### AWS

AWS 파일은  src/main/resource/aws-architecture.png 에 포함했습니다.


