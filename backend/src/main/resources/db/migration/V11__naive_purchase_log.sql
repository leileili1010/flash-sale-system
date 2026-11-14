-- Records purchases accepted by the deliberately naive benchmark endpoint, so load tests can
-- measure how far a read-then-write implementation oversells. Unused outside benchmarks.
CREATE TABLE naive_purchases (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    drop_id     BIGINT      NOT NULL REFERENCES drops (id),
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX naive_purchases_drop_idx ON naive_purchases (drop_id);
