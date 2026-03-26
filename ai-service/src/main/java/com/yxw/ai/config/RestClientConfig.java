package com.yxw.ai.config;

import com.yxw.common.core.security.SecurityContextUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    @Profile("!discovery")
    public RestClient foodServiceRestClientDirect(@Value("${food.service.base-url}") String foodServiceBaseUrl) {
        return createBuilder()
                .baseUrl(foodServiceBaseUrl)
                .build();
    }

    @Bean
    @Profile("discovery")
    public RestClient foodServiceRestClientDiscovery(@LoadBalanced RestClient.Builder restClientBuilder,
                                                     @Value("${food.service.base-url}") String foodServiceBaseUrl) {
        return restClientBuilder
                .baseUrl(foodServiceBaseUrl)
                .build();
    }

    @Bean
    @Profile("discovery")
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return createBuilder();
    }

    private RestClient.Builder createBuilder() {
        return RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    SecurityContextUtils.currentBearerToken()
                            .ifPresent(token -> request.getHeaders().setBearerAuth(token));
                    return execution.execute(request, body);
                });
    }
}
