package pl.ecommanager.integrations.allegro.api;

import pl.ecommanager.integrations.allegro.client.AllegroClient;
import pl.ecommanager.integrations.allegro.persistence.AllegroConnection;
import pl.ecommanager.integrations.allegro.persistence.AllegroConnectionRepository;
import pl.ecommanager.integrations.allegro.security.TokenCipher;
import pl.ecommanager.integrations.allegro.service.AllegroService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:allegro;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "allegro.client-id=test-client",
    "allegro.client-secret=test-secret",
    "allegro.user-agent=EcomManager/test",
    "allegro.token-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "allegro.refresh-delay-ms=86400000"
})
@AutoConfigureMockMvc
class AllegroIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AllegroConnectionRepository repository;
    @Autowired
    AllegroService service;
    @Autowired
    TokenCipher cipher;
    @MockitoBean
    AllegroClient client;

    @BeforeEach
    void resetConnection() {
        var connection = new AllegroConnection();
        connection.setId(1L);
        repository.saveAndFlush(connection);
    }

    @Test
    void completesAuthorizationAndPersistsEncryptedTokens() throws Exception {
        var session = new MockHttpSession();
        String state = authorize(session);
        when(client.exchange(eq("test-code"), anyString())).thenReturn(tokens("access", "refresh"));
        when(client.me("access")).thenReturn(new AllegroClient.Account("123", "bator"));

        mvc.perform(get("/api/integrations/allegro/callback").session(session)
                .param("state", state).param("code", "test-code"))
                .andExpect(redirectedUrl("http://localhost:5173/?allegro=connected"));

        AllegroConnection saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getAccessToken()).isNotEqualTo("access");
        assertThat(saved.getRefreshToken()).isNotEqualTo("refresh");
        assertThat(cipher.decrypt(saved.getAccessToken())).isEqualTo("access");
        assertThat(cipher.decrypt(saved.getRefreshToken())).isEqualTo("refresh");
        var result = mvc.perform(get("/api/integrations/allegro/status"))
                .andExpect(jsonPath("$.state").value("CONNECTED"))
                .andExpect(jsonPath("$.login").value("bator"))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("accessToken", "refreshToken", "test-secret");

        mvc.perform(get("/api/integrations/allegro/callback").session(session)
                .param("state", state).param("code", "test-code"))
                .andExpect(redirectedUrl("http://localhost:5173/?allegro=invalid_state"));
        verify(client).exchange(eq("test-code"), anyString());
    }

    @Test
    void rejectsMissingSessionWrongStateAndExpiredAttempt() throws Exception {
        mvc.perform(get("/api/integrations/allegro/callback").param("state", "wrong").param("code", "code"))
                .andExpect(redirectedUrl("http://localhost:5173/?allegro=invalid_state"));
        var session = new MockHttpSession();
        String state = authorize(session);
        mvc.perform(get("/api/integrations/allegro/callback").session(session)
                .param("state", "wrong").param("code", "code"))
                .andExpect(redirectedUrl("http://localhost:5173/?allegro=invalid_state"));
        session.setAttribute("allegro.oauth.attempt",
                new AllegroController.Attempt(state, "verifier", Instant.now().minusSeconds(1)));
        mvc.perform(get("/api/integrations/allegro/callback").session(session)
                .param("state", state).param("code", "code"))
                .andExpect(redirectedUrl("http://localhost:5173/?allegro=invalid_state"));
        verifyNoInteractions(client);
    }

    @Test
    void handlesDeniedConsent() throws Exception {
        var session = new MockHttpSession();
        String state = authorize(session);
        mvc.perform(get("/api/integrations/allegro/callback").session(session)
                .param("state", state).param("error", "access_denied"))
                .andExpect(redirectedUrl("http://localhost:5173/?allegro=denied"));
        verifyNoInteractions(client);
    }

    @Test
    void rejectsAuthorizationFromAnotherOrigin() throws Exception {
        mvc.perform(post("/api/integrations/allegro/authorize").header("Origin", "https://other.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/integrations/allegro/authorize"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
        "invalid_scope, invalid_scope",
        "invalid_request, invalid_request",
        "unauthorized_client, unauthorized_client",
        "unsupported_response_type, unsupported_response_type",
        "server_error, server_error",
        "temporarily_unavailable, temporarily_unavailable",
        "untrusted-value, authorization_error"
    })
    void reportsAuthorizationErrorsWithoutTreatingThemAsDeniedConsent(String error, String result) throws Exception {
        var session = new MockHttpSession();
        String state = authorize(session);
        mvc.perform(get("/api/integrations/allegro/callback").session(session)
                .param("state", state).param("error", error))
                .andExpect(redirectedUrl("http://localhost:5173/?allegro=" + result));
        verifyNoInteractions(client);
    }

    @Test
    void reportsMissingCodeSeparately() throws Exception {
        var session = new MockHttpSession();
        String state = authorize(session);
        mvc.perform(get("/api/integrations/allegro/callback").session(session).param("state", state))
                .andExpect(redirectedUrl("http://localhost:5173/?allegro=missing_code"));
        verifyNoInteractions(client);
    }

    @Test
    void failedAccountCheckPreservesExistingConnection() throws Exception {
        saveExpiringConnection();
        var session = new MockHttpSession();
        String state = authorize(session);
        when(client.exchange(eq("code"), anyString())).thenReturn(tokens("new-access", "new-refresh"));
        when(client.me("new-access")).thenThrow(new IllegalStateException("remote failure"));
        mvc.perform(get("/api/integrations/allegro/callback").session(session)
                .param("state", state).param("code", "code"))
                .andExpect(redirectedUrl("http://localhost:5173/?allegro=error"));
        assertThat(cipher.decrypt(repository.findById(1L).orElseThrow().getRefreshToken())).isEqualTo("old-refresh");
    }

    @Test
    void rotatesTokensAndSkipsRefreshForFreshToken() {
        saveExpiringConnection();
        when(client.refresh("old-refresh")).thenReturn(tokens("new-access", "new-refresh"));
        service.refreshExpiringToken();
        service.refreshExpiringToken();
        AllegroConnection saved = repository.findById(1L).orElseThrow();
        assertThat(cipher.decrypt(saved.getRefreshToken())).isEqualTo("new-refresh");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusSeconds(3500));
        verify(client).refresh("old-refresh");
    }

    @Test
    void temporaryFailureKeepsRefreshTokenForRetry() {
        saveExpiringConnection();
        when(client.refresh("old-refresh")).thenThrow(new IllegalStateException("timeout"));
        service.refreshExpiringToken();
        AllegroConnection saved = repository.findById(1L).orElseThrow();
        assertThat(cipher.decrypt(saved.getRefreshToken())).isEqualTo("old-refresh");
        assertThat(service.status().state()).isEqualTo("REFRESH_ERROR");
    }

    @Test
    void invalidRefreshTokenRequiresReauthorization() {
        saveExpiringConnection();
        when(client.refresh("old-refresh")).thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "{\"error\":\"invalid_grant\"}".getBytes(), null));
        service.refreshExpiringToken();
        AllegroConnection saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getAccessToken()).isNull();
        assertThat(saved.getRefreshToken()).isNull();
        assertThat(service.status().state()).isEqualTo("REAUTH_REQUIRED");
    }

    private String authorize(MockHttpSession session) throws Exception {
        String result = mvc.perform(post("/api/integrations/allegro/authorize")
                .header("Origin", "http://localhost:5173").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = mapper.readTree(result).get("url").asText();
        assertThat(url).startsWith(AllegroClient.AUTH_URL + "/authorize?")
                .contains("code_challenge_method=S256").doesNotContain("test-secret", "code_verifier");
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
    }

    private void saveExpiringConnection() {
        var connection = new AllegroConnection();
        connection.setId(1L);
        connection.setLogin("bator");
        connection.setAccessToken(cipher.encrypt("old-access"));
        connection.setRefreshToken(cipher.encrypt("old-refresh"));
        connection.setExpiresAt(Instant.now().plusSeconds(30));
        repository.saveAndFlush(connection);
    }

    private AllegroClient.TokenResponse tokens(String access, String refresh) {
        return new AllegroClient.TokenResponse(access, refresh, 3600);
    }
}
