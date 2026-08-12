package com.toir.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.function.Consumer;

public class ToirAiGatewayClient {

    private final ToirAiGatewayProperties properties;
    private final RestClient restClient;

    public ToirAiGatewayClient(ToirAiGatewayProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public JsonNode postJson(String path, Object body) {
        return restClient.post()
                .uri(path)
                .headers(authHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode postMultipart(String path, MultiValueMap<String, Object> multipart) {
        return restClient.post()
                .uri(path)
                .headers(authHeaders())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .body(multipart)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getJson(String path, Map<String, ?> query) {
        String uri = UriComponentsBuilder.fromPath(path)
                .queryParams(toMultiValue(query))
                .build(true)
                .toUriString();
        return restClient.get()
                .uri(uri)
                .headers(authHeaders())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
    }

    public ResponseEntity<byte[]> getRaw(String path, Map<String, ?> query) {
        String uri = UriComponentsBuilder.fromPath(path)
                .queryParams(toMultiValue(query))
                .build(true)
                .toUriString();
        return restClient.get()
                .uri(uri)
                .headers(authHeaders())
                .retrieve()
                .toEntity(byte[].class);
    }

    public static MultiValueMap<String, Object> multipartFile(
            String fieldName,
            byte[] bytes,
            String filename,
            Map<String, String> extraFields
    ) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        if (extraFields != null) {
            extraFields.forEach(body::add);
        }
        body.add(fieldName, new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename != null ? filename : fieldName;
            }
        });
        return body;
    }

    private Consumer<HttpHeaders> authHeaders() {
        return headers -> {
            if (properties.hasAuthentication()) {
                headers.set(properties.getAuthenticationHeader(), properties.getAuthenticationSecret());
            }
        };
    }

    private static MultiValueMap<String, String> toMultiValue(Map<String, ?> query) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (query == null) {
            return params;
        }
        query.forEach((key, value) -> {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                params.add(key, String.valueOf(value));
            }
        });
        return params;
    }
}
