package com.sanjoj.wallet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sanjoj.wallet.account.Account;
import com.sanjoj.wallet.account.AccountService;
import com.sanjoj.wallet.transfer.TransferService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerApiTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private UUID fundedAccount(long cents) {
        Account account = accountService.create("api-test");
        if (cents > 0) {
            accountService.deposit(account.getId(), cents);
        }
        return account.getId();
    }

    private static String transferJson(UUID from, UUID to, long amountCents) {
        return "{\"fromAccountId\":\"" + from + "\",\"toAccountId\":\"" + to
                + "\",\"amountCents\":" + amountCents + "}";
    }

    @Test
    void deposit_returns201_andShowsTheNewBalance() throws Exception {
        UUID id = fundedAccount(0);

        mvc.perform(post("/api/v1/accounts/" + id + "/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":2500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryType").value("DEPOSIT"))
                .andExpect(jsonPath("$.deltaCents").value(2500))
                .andExpect(jsonPath("$.balanceAfterCents").value(2500));
    }

    @Test
    void deposit_ofZero_returns400() throws Exception {
        UUID id = fundedAccount(0);

        mvc.perform(post("/api/v1/accounts/" + id + "/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deposit_toUnknownAccount_returns404() throws Exception {
        mvc.perform(post("/api/v1/accounts/" + UUID.randomUUID() + "/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":100}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void transfer_returns201_thenAReplayReturns200WithTheSameId() throws Exception {
        UUID a = fundedAccount(1_000);
        UUID b = fundedAccount(0);
        String key = UUID.randomUUID().toString();
        String body = transferJson(a, b, 250);

        MvcResult first = mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountCents").value(250))
                .andReturn();
        String transferId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transferId));

        mvc.perform(get("/api/v1/accounts/" + a))
                .andExpect(jsonPath("$.balanceCents").value(750));
    }

    @Test
    void transfer_withoutAnIdempotencyKey_returns400() throws Exception {
        UUID a = fundedAccount(1_000);
        UUID b = fundedAccount(0);

        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(a, b, 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void transfer_withInsufficientFunds_returns422() throws Exception {
        UUID a = fundedAccount(100);
        UUID b = fundedAccount(0);

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(a, b, 500)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void transfer_toTheSameAccount_returns422() throws Exception {
        UUID a = fundedAccount(100);

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(a, a, 10)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));
    }

    @Test
    void transfer_reusingAKeyForADifferentBody_returns422() throws Exception {
        UUID a = fundedAccount(1_000);
        UUID b = fundedAccount(0);
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(a, b, 100)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(a, b, 200)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void transfer_withZeroAmount_returns400() throws Exception {
        UUID a = fundedAccount(100);
        UUID b = fundedAccount(0);

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(a, b, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void transfer_withMalformedJson_returns400() throws Exception {
        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void getTransfer_withUnknownId_returns404() throws Exception {
        mvc.perform(get("/api/v1/transfers/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
    }

    @Test
    void history_isNewestFirst() throws Exception {
        UUID a = fundedAccount(1_000);
        UUID b = fundedAccount(0);
        transferService.transfer(a, b, 300, UUID.randomUUID().toString());

        mvc.perform(get("/api/v1/accounts/" + a + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entryType").value("TRANSFER_DEBIT"))
                .andExpect(jsonPath("$[0].deltaCents").value(-300))
                .andExpect(jsonPath("$[0].balanceAfterCents").value(700))
                .andExpect(jsonPath("$[1].entryType").value("DEPOSIT"));
    }

    @Test
    void history_returnsTheRequestedPage() throws Exception {
        UUID id = fundedAccount(0);
        accountService.deposit(id, 1);
        accountService.deposit(id, 2);
        accountService.deposit(id, 3);

        mvc.perform(get("/api/v1/accounts/" + id + "/transactions?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].deltaCents").value(3));

        mvc.perform(get("/api/v1/accounts/" + id + "/transactions?page=1&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deltaCents").value(1));
    }

    @Test
    void history_withAPageSizeOutsideTheLimits_returns400() throws Exception {
        UUID id = fundedAccount(0);

        mvc.perform(get("/api/v1/accounts/" + id + "/transactions?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGINATION_INVALID"));

        mvc.perform(get("/api/v1/accounts/" + id + "/transactions?size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGINATION_INVALID"));
    }
}
