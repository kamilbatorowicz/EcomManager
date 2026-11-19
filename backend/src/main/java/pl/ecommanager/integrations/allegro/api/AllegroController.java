package pl.ecommanager.integrations.allegro.api;

import pl.ecommanager.integrations.allegro.client.AllegroClient;
import pl.ecommanager.integrations.allegro.config.AllegroProperties;
import pl.ecommanager.integrations.allegro.service.AllegroService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/integrations/allegro")
class AllegroController {
    private static final String ATTEMPT = "allegro.oauth.attempt";
    private final SecureRandom random = new SecureRandom();
    private final AllegroProperties properties;
    private final AllegroService service;

    AllegroController(AllegroProperties properties, AllegroService service) {
        this.properties = properties;
        this.service = service;
    }

    @GetMapping("/status")
    ResponseEntity<AllegroService.Status> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status());
    }

    @PostMapping("/authorize")
    ResponseEntity<?> authorize(HttpServletRequest request) throws NoSuchAlgorithmException {
        if (!properties.frontendUri().equals(request.getHeader(HttpHeaders.ORIGIN))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Nieprawidłowe źródło żądania."));
        }
        if (!properties.configured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Uzupełnij konfigurację Allegro w backendzie."));
        }
        var attempt = new Attempt(randomValue(), randomValue(), Instant.now().plusSeconds(600));
        request.getSession().setAttribute(ATTEMPT, attempt);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(attempt.verifier().getBytes(StandardCharsets.US_ASCII)));
        String url = UriComponentsBuilder.fromUriString(AllegroClient.AUTH_URL + "/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("state", attempt.state())
                .queryParam("scope", "allegro:api:profile:read")
                .queryParam("code_challenge_method", "S256")
                .queryParam("code_challenge", challenge)
                .build().encode().toUriString();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("url", url));
    }

    @GetMapping("/callback")
    ResponseEntity<Void> callback(@RequestParam(required = false) String state,
            @RequestParam(required = false) String code, @RequestParam(required = false) String error,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Attempt attempt = consumeAttempt(session, state);
        if (attempt == null) {
            return redirect("invalid_state");
        }
        if (error != null) {
            return redirect(switch (error) {
                case "access_denied" -> "denied";
                case "invalid_scope", "invalid_request", "unauthorized_client", "unsupported_response_type",
                        "server_error", "temporarily_unavailable" -> error;
                default -> "authorization_error";
            });
        }
        if (code == null || code.isBlank()) {
            return redirect("missing_code");
        }
        try {
            service.connect(code, attempt.verifier());
            return redirect("connected");
        } catch (RuntimeException exception) {
            return redirect("error");
        }
    }

    private Attempt consumeAttempt(HttpSession session, String state) {
        if (session == null || state == null) {
            return null;
        }
        synchronized (session) {
            Attempt attempt = (Attempt) session.getAttribute(ATTEMPT);
            if (attempt == null || !attempt.expiresAt().isAfter(Instant.now())
                    || !MessageDigest.isEqual(attempt.state().getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            session.removeAttribute(ATTEMPT);
            return attempt;
        }
    }

    private String randomValue() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private ResponseEntity<Void> redirect(String result) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Referrer-Policy", "no-referrer")
                .cacheControl(CacheControl.noStore())
                .location(URI.create(properties.frontendUri() + "/?allegro=" + result)).build();
    }

    record Attempt(String state, String verifier, Instant expiresAt) {
    }
}
