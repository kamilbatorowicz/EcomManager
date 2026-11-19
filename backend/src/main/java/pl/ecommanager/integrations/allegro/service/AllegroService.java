package pl.ecommanager.integrations.allegro.service;

import pl.ecommanager.integrations.allegro.client.AllegroClient;
import pl.ecommanager.integrations.allegro.config.AllegroProperties;
import pl.ecommanager.integrations.allegro.persistence.AllegroConnection;
import pl.ecommanager.integrations.allegro.persistence.AllegroConnectionRepository;
import pl.ecommanager.integrations.allegro.security.TokenCipher;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AllegroService {
    private final AllegroProperties properties;
    private final AllegroClient client;
    private final AllegroConnectionRepository repository;
    private final TokenCipher cipher;

    AllegroService(AllegroProperties properties, AllegroClient client,
            AllegroConnectionRepository repository, TokenCipher cipher) {
        this.properties = properties;
        this.client = client;
        this.repository = repository;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public Status status() {
        if (!properties.configured()) {
            return new Status("NOT_CONFIGURED", null, null);
        }
        AllegroConnection connection = repository.findById(1L).orElseThrow();
        if (connection.getAccessToken() == null) {
            return new Status(connection.getLastError() == null ? "DISCONNECTED" : "REAUTH_REQUIRED", connection.getLogin(), null);
        }
        if (!connection.getExpiresAt().isAfter(Instant.now())) {
            return new Status("EXPIRED", connection.getLogin(), connection.getExpiresAt());
        }
        return new Status(connection.getLastError() == null ? "CONNECTED" : "REFRESH_ERROR", connection.getLogin(), connection.getExpiresAt());
    }

    @Transactional
    public void connect(String code, String verifier) {
        AllegroConnection connection = repository.lockConnection();
        AllegroClient.TokenResponse tokens = client.exchange(code, verifier);
        AllegroClient.Account account = client.me(tokens.accessToken());
        if (account == null || account.id() == null || account.login() == null) {
            throw new IllegalStateException("Nieprawidłowa odpowiedź konta Allegro.");
        }
        connection.setAccountId(account.id());
        connection.setLogin(account.login());
        saveTokens(connection, tokens);
    }

    @Scheduled(fixedDelayString = "${allegro.refresh-delay-ms:60000}", initialDelayString = "${allegro.refresh-delay-ms:60000}")
    @Transactional
    public void refreshExpiringToken() {
        if (!properties.configured()) {
            return;
        }
        AllegroConnection connection = repository.lockConnection();
        if (connection.getRefreshToken() == null || connection.getExpiresAt().isAfter(Instant.now().plusSeconds(120))) {
            return;
        }
        try {
            saveTokens(connection, client.refresh(cipher.decrypt(connection.getRefreshToken())));
        } catch (RestClientResponseException exception) {
            connection.setLastError("REFRESH_FAILED");
            if (exception.getStatusCode().value() == 400
                    && exception.getResponseBodyAsString().contains("invalid_grant")) {
                connection.setAccessToken(null);
                connection.setRefreshToken(null);
                connection.setExpiresAt(null);
            }
        } catch (RuntimeException exception) {
            connection.setLastError("REFRESH_FAILED");
        }
    }

    private void saveTokens(AllegroConnection connection, AllegroClient.TokenResponse tokens) {
        String accessToken = cipher.encrypt(tokens.accessToken());
        String refreshToken = cipher.encrypt(tokens.refreshToken());
        connection.setAccessToken(accessToken);
        connection.setRefreshToken(refreshToken);
        connection.setExpiresAt(Instant.now().plusSeconds(tokens.expiresIn()));
        connection.setLastError(null);
    }

    public record Status(String state, String login, Instant expiresAt) {
    }
}
