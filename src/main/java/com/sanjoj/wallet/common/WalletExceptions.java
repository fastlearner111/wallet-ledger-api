package com.sanjoj.wallet.common;

import java.util.UUID;

// Every error the API can return on purpose. ApiExceptionHandler turns each one into an HTTP response.
public final class WalletExceptions {

    private WalletExceptions() {
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(UUID id) {
            super("Account " + id + " does not exist");
        }
    }

    public static class TransferNotFoundException extends RuntimeException {
        public TransferNotFoundException(UUID id) {
            super("Transfer " + id + " does not exist");
        }
    }

    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(UUID accountId, long balanceCents, long amountCents) {
            super("Account " + accountId + " has " + balanceCents + " cents but the transfer needs " + amountCents);
        }
    }

    public static class SameAccountException extends RuntimeException {
        public SameAccountException() {
            super("A transfer needs two different accounts");
        }
    }

    public static class IdempotencyKeyReusedException extends RuntimeException {
        public IdempotencyKeyReusedException(String key) {
            super("Idempotency key '" + key + "' was already used with a different request");
        }
    }

    public static class InvalidRequestException extends RuntimeException {
        private final String code;

        public InvalidRequestException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
