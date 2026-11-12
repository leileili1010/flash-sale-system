-- Core schema for the flash sale system.
-- Correctness rules are enforced by the database itself (CHECK constraints and partial
-- unique indexes), so a bug or a duplicated message in the application cannot oversell
-- stock or create a second live order for the same customer.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'))
);

CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE products (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku          VARCHAR(40)   NOT NULL UNIQUE,
    name         VARCHAR(120)  NOT NULL,
    series       VARCHAR(120)  NOT NULL,
    product_type VARCHAR(20)   NOT NULL,
    description  TEXT          NOT NULL,
    image_url    VARCHAR(500)  NOT NULL,
    retail_price NUMERIC(10, 2) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT products_type_check CHECK (product_type IN ('BLIND_BOX', 'FIGURE', 'PLUSH')),
    CONSTRAINT products_price_check CHECK (retail_price > 0)
);

-- A drop is a time-boxed, limited-quantity release of one product.
-- Stock accounting: every unit is exactly one of available, locked (reserved by an order
-- awaiting payment) or sold, and the CHECK constraint makes that invariant unbreakable.
CREATE TABLE drops (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      BIGINT        NOT NULL REFERENCES products (id),
    title           VARCHAR(160)  NOT NULL,
    drop_price      NUMERIC(10, 2) NOT NULL,
    original_price  NUMERIC(10, 2) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    start_time      TIMESTAMPTZ   NOT NULL,
    end_time        TIMESTAMPTZ   NOT NULL,
    total_stock     INTEGER       NOT NULL,
    available_stock INTEGER       NOT NULL,
    locked_stock    INTEGER       NOT NULL DEFAULT 0,
    sold_stock      INTEGER       NOT NULL DEFAULT 0,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT drops_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT drops_window_check CHECK (end_time > start_time),
    CONSTRAINT drops_price_check CHECK (drop_price > 0 AND original_price > 0),
    CONSTRAINT drops_stock_non_negative CHECK (
        total_stock >= 0 AND available_stock >= 0 AND locked_stock >= 0 AND sold_stock >= 0),
    CONSTRAINT drops_stock_balance CHECK (
        available_stock + locked_stock + sold_stock = total_stock)
) WITH (fillfactor = 80); -- leaves page space for HOT updates on frequently-updated stock rows

CREATE INDEX drops_product_idx ON drops (product_id);
CREATE INDEX drops_status_start_idx ON drops (status, start_time);

CREATE TABLE orders (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_no       BIGINT        NOT NULL UNIQUE, -- Snowflake id assigned at the API edge; idempotency key
    drop_id        BIGINT        NOT NULL REFERENCES drops (id),
    user_id        BIGINT        NOT NULL REFERENCES users (id),
    amount         NUMERIC(10, 2) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    failure_reason VARCHAR(100),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ   NOT NULL,
    paid_at        TIMESTAMPTZ,
    closed_at      TIMESTAMPTZ,
    CONSTRAINT orders_status_check CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CLOSED', 'FAILED')),
    CONSTRAINT orders_amount_check CHECK (amount > 0)
);

-- One live (unpaid or paid) order per customer per drop. Closed and failed orders are
-- excluded so a customer whose order expired may try again while stock remains.
CREATE UNIQUE INDEX orders_one_live_per_user_uq
    ON orders (drop_id, user_id)
    WHERE status IN ('PENDING_PAYMENT', 'PAID');

-- Small index that only covers orders the expiry sweeper needs to look at.
CREATE INDEX orders_pending_expiry_idx
    ON orders (expires_at)
    WHERE status = 'PENDING_PAYMENT';

CREATE INDEX orders_user_created_idx ON orders (user_id, created_at DESC);

-- Distributed lock table for scheduled jobs (ShedLock), so only one instance runs each job.
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
