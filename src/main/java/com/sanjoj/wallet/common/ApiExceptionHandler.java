package com.sanjoj.wallet.common;

import com.sanjoj.wallet.common.WalletExceptions.AccountNotFoundException;
import com.sanjoj.wallet.common.WalletExceptions.IdempotencyKeyReusedException;
import com.sanjoj.wallet.common.WalletExceptions.InsufficientFundsException;
import com.sanjoj.wallet.common.WalletExceptions.InvalidRequestException;
import com.sanjoj.wallet.common.WalletExceptions.SameAccountException;
import com.sanjoj.wallet.common.WalletExceptions.TransferNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Every error leaves the API as an RFC 9457 ProblemDetail with a stable "code" field.
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Account not found", "ACCOUNT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ProblemDetail handleTransferNotFound(TransferNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Transfer not found", "TRANSFER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds", "INSUFFICIENT_FUNDS", ex.getMessage());
    }

    @ExceptionHandler(SameAccountException.class)
    public ProblemDetail handleSameAccount(SameAccountException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Same account", "SAME_ACCOUNT", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ProblemDetail handleKeyReused(IdempotencyKeyReusedException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused",
                "IDEMPOTENCY_KEY_REUSED", ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Missing header", "MISSING_HEADER",
                "Required header is missing: " + ex.getHeaderName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "MALFORMED_REQUEST",
                "Request body is missing or is not valid JSON");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "VALIDATION_FAILED", "Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
