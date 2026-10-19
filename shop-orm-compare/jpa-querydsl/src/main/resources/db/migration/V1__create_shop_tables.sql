-- 스키마의 단일 출처(source of truth)는 Flyway다.
-- JPA 엔티티(ddl-auto=validate)와 Exposed 테이블 객체(MigrationUtils 검증)는 이 스키마를 "매핑"할 뿐 DDL을 만들지 않는다.
-- 세 프로젝트(jpa-querydsl, exposed-dao, exposed-dsl)가 같은 내용의 이 파일을 각자 갖고, 같은 DB에 적용한다 (Flyway 체크섬이 같아야 함).
-- FK 동작(ON DELETE/UPDATE)을 생략하면 MySQL은 NO ACTION으로 기록하고 Exposed 기본값(RESTRICT)과 달라 검증에 걸린다 → 명시한다.

CREATE TABLE users
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE items
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    price      BIGINT       NOT NULL,
    stock      INT          NOT NULL,
    version    BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL
);

CREATE INDEX idx_items_name ON items (name);

CREATE TABLE orders
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    total_amount BIGINT      NOT NULL,
    ordered_at   DATETIME(6) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    CONSTRAINT fk_orders_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- 사용자별 주문 목록(keyset: user_id = ? AND id < ? ORDER BY id DESC)
CREATE INDEX idx_orders_user_id_id ON orders (user_id, id);
-- 상태별 주문 목록 · 기간별 판매 통계
CREATE INDEX idx_orders_status_ordered_at ON orders (status, ordered_at);

CREATE TABLE order_items
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT       NOT NULL,
    item_id    BIGINT       NOT NULL,
    item_name  VARCHAR(100) NOT NULL, -- 주문 시점 스냅샷 (상품명이 바뀌어도 주문 내역은 유지)
    unit_price BIGINT       NOT NULL, -- 주문 시점 스냅샷
    quantity   INT          NOT NULL,
    CONSTRAINT fk_order_items_order_id FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_order_items_item_id FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
