package com.sanjoj.wallet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanjoj.wallet.account.Account;
import com.sanjoj.wallet.account.AccountService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountApiTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AccountService accountService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createAccount_returns201_withZeroBalance() throws Exception {
        mvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ownerName").value("Alice"))
                .andExpect(jsonPath("$.balanceCents").value(0));
    }

    @Test
    void createAccount_withBlankName_returns400() throws Exception {
        mvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void getAccount_returnsTheAccount() throws Exception {
        Account saved = accountService.create("Bob");

        mvc.perform(get("/api/v1/accounts/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Bob"))
                .andExpect(jsonPath("$.balanceCents").value(0));
    }

    @Test
    void getAccount_withUnknownId_returns404() throws Exception {
        mvc.perform(get("/api/v1/accounts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }
}
