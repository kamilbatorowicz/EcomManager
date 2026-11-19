package pl.ecommanager.integrations.allegro.client;

import pl.ecommanager.integrations.allegro.config.AllegroProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

class AllegroClientTest {
    @Test
    void usesSandboxHeadersPkceAndRotatingRefreshTokens() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new AllegroProperties("id", "secret", "EcomManager/test", "", "http://localhost/callback", "http://localhost:5173");
        var client = new AllegroClient(builder.build(), properties);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", "code");
        form.add("redirect_uri", "http://localhost/callback");
        form.add("code_verifier", "verifier");
        form.add("client_id", "id");

        server.expect(requestTo(AllegroClient.AUTH_URL + "/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("User-Agent", "EcomManager/test"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().formData(form))
                .andRespond(withSuccess("{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.allegro.pl.allegrosandbox.pl/me"))
                .andExpect(header("User-Agent", "EcomManager/test"))
                .andExpect(header("Authorization", "Bearer access"))
                .andExpect(header("Accept", "application/vnd.allegro.public.v1+json"))
                .andRespond(withSuccess("{\"id\":\"123\",\"login\":\"bator\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(AllegroClient.AUTH_URL + "/token"))
                .andExpect(header("Authorization", "Basic aWQ6c2VjcmV0"))
                .andExpect(content().string("grant_type=refresh_token&refresh_token=refresh"))
                .andRespond(withSuccess("{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        assertThat(client.exchange("code", "verifier").accessToken()).isEqualTo("access");
        assertThat(client.me("access").login()).isEqualTo("bator");
        assertThat(client.refresh("refresh").refreshToken()).isEqualTo("new-refresh");
        server.verify();
    }
}
