package pl.ecommanager.integrations.allegro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "allegro")
public record AllegroProperties(String clientId, String clientSecret, String userAgent,
        String tokenEncryptionKey, String redirectUri, String frontendUri) {
    public AllegroProperties {
        clientId = unquote(clientId);
        clientSecret = unquote(clientSecret);
        userAgent = unquote(userAgent);
        tokenEncryptionKey = unquote(tokenEncryptionKey);
    }

    public boolean configured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !userAgent.isBlank()
                && !tokenEncryptionKey.isBlank();
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
