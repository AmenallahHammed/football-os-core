package com.fos.sdk.canonical;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
public class CanonicalAutoConfiguration {

    @Bean
    public CanonicalServiceClient canonicalServiceClient(RestClient.Builder builder,
            @org.springframework.beans.factory.annotation.Value(
                "${fos.canonical.service-url:http://localhost:8081}") String baseUrl) {
        return new CanonicalServiceClient(builder, baseUrl);
    }

    @Bean
    public CanonicalResolver canonicalResolver(CanonicalServiceClient client) {
        return new CanonicalResolver(client);
    }
}
