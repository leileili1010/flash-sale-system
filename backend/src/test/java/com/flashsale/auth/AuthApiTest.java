package com.flashsale.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.support.TestcontainersConfiguration;
import com.flashsale.user.Role;
import com.flashsale.user.User;
import com.flashsale.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JsonMapper json;

    @BeforeEach
    void cleanUsers() {
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void registerReturnsATokenThatAuthenticatesTheUser() throws Exception {
        String token = tokenFrom(register("Ada@Example.com", "correct-horse", "Ada")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER")));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada"));
    }

    @Test
    void passwordsAreStoredHashed() throws Exception {
        register("ada@example.com", "correct-horse", "Ada").andExpect(status().isCreated());

        User stored = users.findByEmail("ada@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("correct-horse").startsWith("$2");
    }

    @Test
    void emailsAreUniqueRegardlessOfCase() throws Exception {
        register("ada@example.com", "correct-horse", "Ada").andExpect(status().isCreated());
        register("ADA@example.com", "another-pass", "Imposter")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void rejectsInvalidRegistrations() throws Exception {
        register("not-an-email", "short", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.displayName").exists());
    }

    @Test
    void loginSucceedsWithCorrectCredentials() throws Exception {
        register("ada@example.com", "correct-horse", "Ada");
        login("ADA@example.com", "correct-horse")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void loginFailsIdenticallyForWrongPasswordAndUnknownEmail() throws Exception {
        register("ada@example.com", "correct-horse", "Ada");
        login("ada@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("nobody@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tamperedTokensAreRejected() throws Exception {
        String token = tokenFrom(register("ada@example.com", "correct-horse", "Ada"));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void catalogIsPublic() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/drops")).andExpect(status().isOk());
        mockMvc.perform(get("/api/time")).andExpect(status().isOk());
    }

    @Test
    void adminRoutesRequireTheAdminRole() throws Exception {
        String shopper = tokenFrom(register("ada@example.com", "correct-horse", "Ada"));
        mockMvc.perform(get("/api/admin/anything").header("Authorization", "Bearer " + shopper))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        users.save(new User("root@example.com", passwordEncoder.encode("admin-pass"), "Root", Role.ADMIN, Instant.now()));
        String admin = tokenFrom(login("root@example.com", "admin-pass"));
        // Passes authorization and reaches routing; no admin endpoint exists at this path yet.
        mockMvc.perform(get("/api/admin/anything").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    private ResultActions register(String email, String password, String displayName) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegisterRequest(email, password, displayName))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password))));
    }

    private String tokenFrom(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asString();
    }
}
