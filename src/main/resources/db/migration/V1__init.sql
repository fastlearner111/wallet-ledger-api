-- Wallet ledger schema (decisions D5, D6, D7, D8, D10 in PLAN.md).
-- Money is stored as integer cents. The CHECK constraints are the last line of defense.

CREATE TABLE accounts (
    id            UUID         PRIMARY KEY,
    owner_name    VARCHAR(100) NOT NULL,
    balance_cents BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT accounts_balance_non_negative CHECK (balance_cents >= 0)
);

CREATE TABLE transfers (
    id              UUID        PRIMARY KEY,
    from_account_id UUID        NOT NULL REFERENCES accounts (id),
    to_account_id   UUID        NOT NULL REFERENCES accounts (id),
    amount_cents    BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT transfers_amount_positive CHECK (amount_cents > 0),
    CONSTRAINT transfers_different_accounts CHECK (from_account_id <> to_account_id)
);

-- Append-only history. Rows are inserted and never updated or deleted.
-- seq gives a strict insertion order. Timestamps alone can tie, and a UUID is random.
CREATE TABLE ledger_entries (
    id                  UUID        PRIMARY KEY,
    seq                 BIGINT      GENERATED ALWAYS AS IDENTITY,
    account_id          UUID        NOT NULL REFERENCES accounts (id),
    transfer_id         UUID        REFERENCES transfers (id),
    entry_type          VARCHAR(20) NOT NULL,
    delta_cents         BIGINT      NOT NULL,
    balance_after_cents BIGINT      NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ledger_entry_type_valid
        CHECK (entry_type IN ('DEPOSIT', 'TRANSFER_DEBIT', 'TRANSFER_CREDIT'))
);

CREATE INDEX ix_ledger_entries_account_history
    ON ledger_entries (account_id, seq DESC);

-- One row per Idempotency-Key. The primary key is what makes concurrent retries safe.
CREATE TABLE idempotency_keys (
    idempotency_key     VARCHAR(255) PRIMARY KEY,
    request_fingerprint VARCHAR(64)  NOT NULL,
    transfer_id         UUID         REFERENCES transfers (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
