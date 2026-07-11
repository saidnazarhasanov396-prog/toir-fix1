package com.toir.service.faktura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.toir.config.FakturaUzProperties;
import com.toir.exception.RestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakturaUzClientServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void authRequestUsesFormEncodingAndMapsTokenResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        FakturaUzClientService client = client();

        var response = client.getAuthToken("user+name", "test password", "client-id", "client/secret");

        assertThat(response.getAccessToken()).isEqualTo("test-token");
        assertThat(requestBody.get())
                .contains("username=user%2Bname")
                .contains("password=test+password")
                .contains("client_id=client-id")
                .contains("client_secret=client%2Fsecret")
                .contains("grant_type=password");
    }

    @Test
    void malformedSuccessfulResponseUsesExistingBadRequestTranslation() throws Exception {
        startServer(exchange -> {
            byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThatThrownBy(() -> client().getAuthToken("user", "password", "client", "secret"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("FakturaUz auth failed");
    }

    @Test
    void non2xxResponseIncludesCurrentExternalErrorDetails() throws Exception {
        startServer(exchange -> {
            byte[] body = "test-denied".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThatThrownBy(() -> client().getAuthToken("user", "password", "client", "secret"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageContaining("test-denied");
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private FakturaUzClientService client() {
        FakturaUzProperties properties = new FakturaUzProperties();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        properties.getEndpoints().setAuth(baseUrl + "/auth");
        properties.getEndpoints().setGetDocuments(baseUrl + "/documents");
        properties.getEndpoints().setGetDocumentsContent(baseUrl + "/content");
        properties.getEndpoints().setGetUserDetails(baseUrl + "/user-details");
        return new FakturaUzClientService(properties, new ObjectMapper());
    }
}
