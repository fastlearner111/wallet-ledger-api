package com.sanjoj.wallet.transfer;

import com.sanjoj.wallet.transfer.TransferService.TransferResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @Min(1) @Max(100_000_000) long amountCents) {
    }

    public record TransferResponse(UUID id, UUID fromAccountId, UUID toAccountId, long amountCents, Instant createdAt) {
        static TransferResponse from(Transfer transfer) {
            return new TransferResponse(
                    transfer.getId(),
                    transfer.getFromAccountId(),
                    transfer.getToAccountId(),
                    transfer.getAmountCents(),
                    transfer.getCreatedAt());
        }
    }

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    // 201 the first time. A retry with the same key and the same body returns 200 and the original transfer.
    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransferResult result = transferService.transfer(
                request.fromAccountId(), request.toAccountId(), request.amountCents(), idempotencyKey);
        TransferResponse body = TransferResponse.from(result.transfer());
        if (result.replayed()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id) {
        return TransferResponse.from(transferService.get(id));
    }
}
