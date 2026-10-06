# Wallet Ledger API

![CI](https://github.com/fastlearner111/wallet-ledger-api/actions/workflows/ci.yml/badge.svg)

A simulated wallet ledger REST API built with Java 21, Spring Boot 4, and PostgreSQL. Accounts hold balances in whole cents, and money moves between them through transfers. It focuses on two things: a transfer never creates or destroys money, even when many run at once, and retrying a transfer never moves money twice.

It is a learning project. It is not a payment system and it never touches real money.

## What it does

- Create accounts, fund them with a deposit, and read balances
- Transfer money between two accounts. Every transfer needs an `Idempotency-Key` header
- Read an account's transaction history, newest first, with pagination (page size 1 to 100)
- Return errors as RFC 9457 problem details, each with a stable `code` field

## API

| Method and path | Success | Errors |
|---|---|---|
| `POST /api/v1/accounts` | 201 | 400 validation |
| `GET /api/v1/accounts/{id}` | 200 | 404 `ACCOUNT_NOT_FOUND` |
| `POST /api/v1/accounts/{id}/deposits` | 201 | 400, 404 |
| `POST /api/v1/transfers` with header `Idempotency-Key` | 201 first time, 200 on a retry | 400 missing key or bad body, 404 account, 422 `INSUFFICIENT_FUNDS`, `SAME_ACCOUNT`, `IDEMPOTENCY_KEY_REUSED` |
| `GET /api/v1/transfers/{id}` | 200 | 404 `TRANSFER_NOT_FOUND` |
| `GET /api/v1/accounts/{id}/transactions?page=&size=` | 200 | 400 `PAGINATION_INVALID`, 404 |
| `GET /actuator/health` | 200 | 503 if the database is down |

Example:

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' -d '{"ownerName": "Alice"}'

curl -X POST http://localhost:8080/api/v1/transfers \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: 7b1c0d52-1111-4c5e-9d2e-000000000001' \
  -d '{"fromAccountId": "<id>", "toAccountId": "<id>", "amountCents": 2500}'
```

## How it works

- **Money is integer cents.** No floats anywhere. The database also enforces `balance_cents >= 0` and `amount_cents > 0` with CHECK constraints, as a last line of defense.
- **Transfers lock both accounts** with `SELECT ... FOR UPDATE`, always in the same order (by account id), so two opposite transfers cannot deadlock. The balance check runs on the locked, current value.
- **Idempotency** uses a table with the key as its primary key. A transfer first does `INSERT ... ON CONFLICT DO NOTHING` on that key inside the same transaction. If the key already exists, PostgreSQL makes a concurrent request wait for the first one to finish, then the retry returns the stored transfer. A failed transfer rolls back its key, so it can be retried with the same key.
- **The ledger is append-only.** Every deposit and transfer writes `ledger_entries` rows with a signed amount and the balance afterwards. An account's balance always equals the sum of its entries.
- **Flyway** owns the schema and Hibernate only validates that the entities match it.

## Tests

30 tests run against a real PostgreSQL started by Testcontainers, with no mocked repositories. They include:

- 200 random concurrent transfers across three accounts: the total stays the same, no balance goes negative, and every balance equals the sum of its ledger entries
- 200 opposing transfers between two accounts: no deadlock
- The same `Idempotency-Key` sent 10 times at once: exactly one transfer is created
- Failed transfers change nothing and do not burn their key

```bash
./mvnw verify      # needs Docker running
```

## Run it

```bash
docker compose up --build
```

The API is on http://localhost:8080 and PostgreSQL is on port 5433.

## How this was built

I wrote the design first: the data model, the locking order, the idempotency approach, and the API contract. The first version of the code was generated with an AI assistant from that design, and the test suite was run against a real PostgreSQL to check it.

## Known limitations

- No authentication: any caller can move money between any accounts
- Single currency, no refunds or reversals
- Deposits are a funding stub and are not idempotent
- Idempotency keys are global (not per client) and never expire
- The ledger is append-only by convention, and the database does not enforce it
- Pagination bounds are checked by hand in the controller
