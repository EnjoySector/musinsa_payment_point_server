INSERT INTO point_user (
    id, name, created_at, updated_at
) VALUES
    (1, 'test-user-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'limit-test-user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'admin-test-user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO point_policy (
    id,
    policy_code,
    name,
    max_earn_amount,
    default_expire_days,
    min_expire_days,
    max_expire_days,
    status,
    status_updated_at,
    status_updated_by,
    created_at,
    updated_at
) VALUES (
    1,
    'DEFAULT',
    '기본 포인트 정책',
    100000,
    365,
    1,
    1824,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO point_user_policy (
    id,
    policy_code,
    name,
    max_balance_amount,
    status,
    status_updated_at,
    status_updated_by,
    created_at,
    updated_at
) VALUES
    (
        1,
        'DEFAULT',
        '기본 사용자 포인트 정책',
        1000000,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        2,
        'LOW_LIMIT',
        '낮은 보유 한도 테스트 정책',
        1500,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

INSERT INTO point_account (
    id,
    user_id,
    point_user_policy_id,
    status,
    status_updated_at,
    status_updated_by,
    created_at,
    updated_at
) VALUES
    (
        1,
        1,
        1,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        2,
        2,
        2,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        3,
        3,
        1,
        'ACTIVE',
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

INSERT INTO point_balance (
    account_id,
    balance_amount,
    updated_at
) VALUES
    (1, 0, CURRENT_TIMESTAMP),
    (2, 0, CURRENT_TIMESTAMP),
    (3, 0, CURRENT_TIMESTAMP);

ALTER TABLE point_user ALTER COLUMN id RESTART WITH 4;
ALTER TABLE point_policy ALTER COLUMN id RESTART WITH 2;
ALTER TABLE point_user_policy ALTER COLUMN id RESTART WITH 3;
ALTER TABLE point_account ALTER COLUMN id RESTART WITH 4;
