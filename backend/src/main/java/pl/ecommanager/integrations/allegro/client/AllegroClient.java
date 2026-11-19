package pl.ecommanager.integrations.allegro.client;

import pl.ecommanager.integrations.allegro.config.AllegroProperties;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

public class AllegroClient {
    public static final String AUTH_URL = "https://allegro.pl.allegrosandbox.pl/auth/oauth";
    private static final String API_URL = "https://api.allegro.pl.allegrosandbox.pl";
    private final RestClient client;
    private final AllegroProperties properties;

    public AllegroClient(RestClient client, AllegroProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public TokenResponse exchange(String code, String verifier) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        form.add("code_verifier", verifier);
        form.add("client_id", properties.clientId());
        return token(form);
    }

    public TokenResponse refresh(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return token(form);
    }

    public Account me(String accessToken) {
        return client.get().uri(API_URL + "/me")
                .header(HttpHeaders.USER_AGENT, properties.userAgent())
                .header(HttpHeaders.ACCEPT, "application/vnd.allegro.public.v1+json")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve().body(Account.class);
    }

    private TokenResponse token(LinkedMultiValueMap<String, String> form) {
        TokenResponse response = client.post().uri(AUTH_URL + "/token")
                .header(HttpHeaders.USER_AGENT, properties.userAgent())
                .headers(headers -> {
                    if (!form.containsKey("code_verifier")) {
                        headers.setBasicAuth(properties.clientId(), properties.clientSecret());
                    }
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(TokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()
                || response.refreshToken() == null || response.refreshToken().isBlank()
                || response.expiresIn() <= 0) {
            throw new IllegalStateException("Nieprawidłowa odpowiedź autoryzacji Allegro.");
        }
        return response;
    }

    public record TokenResponse(@JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn) {
    }

    public record Account(String id, String login) {
    }
}
