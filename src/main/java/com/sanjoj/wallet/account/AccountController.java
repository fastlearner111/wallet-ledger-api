package com.sanjoj.wallet.account;

import com.sanjoj.wallet.common.WalletExceptions.InvalidRequestException;
import com.sanjoj.wallet.ledger.LedgerEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final int MAX_PAGE_SIZE = 100;

    // Request and response shapes live next to the endpoints that use them.
    public record CreateAccountRequest(@NotBlank @Size(max = 100) String ownerName) {
    }

    public record DepositRequest(@Min(1) @Max(100_000_000) long amountCents) {
    }

    public record AccountResponse(UUID id, String ownerName, long balanceCents, Instant createdAt) {
        static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(), account.getOwnerName(), account.getBalanceCents(), account.getCreatedAt());
        }
    }

    public record LedgerEntryResponse(
            UUID id,
            UUID transferId,
            LedgerEntry.EntryType entryType,
            long deltaCents,
            long balanceAfterCents,
            Instant createdAt) {
        static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(
                    entry.getId(),
                    entry.getTransferId(),
                    entry.getEntryType(),
                    entry.getDeltaCents(),
                    entry.getBalanceAfterCents(),
                    entry.getCreatedAt());
        }
    }

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(request.ownerName());
        return ResponseEntity
                .created(URI.create("/api/v1/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountService.get(id));
    }

    @PostMapping("/{id}/deposits")
    public ResponseEntity<LedgerEntryResponse> deposit(
            @PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
        LedgerEntry entry = accountService.deposit(id, request.amountCents());
        return ResponseEntity.status(HttpStatus.CREATED).body(LedgerEntryResponse.from(entry));
    }

    @GetMapping("/{id}/transactions")
    public List<LedgerEntryResponse> transactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "PAGINATION_INVALID", "page must be 0 or more and size must be 1 to " + MAX_PAGE_SIZE);
        }
        return accountService.history(id, page, size).stream().map(LedgerEntryResponse::from).toList();
    }
}
