package com.bjarne.datingrecommendationsuserservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AuthorizationClientConfiguration {

    @Bean
    public RestClient stsRestClient(
            @Value("${authorization.sts.url}") String stsUrl
    ) {
        return RestClient.builder().baseUrl(stsUrl).build();
    }

}
