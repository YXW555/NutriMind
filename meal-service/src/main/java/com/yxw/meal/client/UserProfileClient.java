package com.yxw.meal.client;

import com.yxw.common.core.ApiResponse;
import com.yxw.meal.client.dto.ProfileOverviewSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserProfileClient {

    private static final ParameterizedTypeReference<ApiResponse<ProfileOverviewSnapshot>> PROFILE_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient userServiceRestClient;

    public UserProfileClient(@Qualifier("userServiceRestClient") RestClient userServiceRestClient) {
        this.userServiceRestClient = userServiceRestClient;
    }

    public ProfileOverviewSnapshot getOverview() {
        ApiResponse<ProfileOverviewSnapshot> response = userServiceRestClient.get()
                .uri("/profile/overview")
                .retrieve()
                .body(PROFILE_RESPONSE_TYPE);
        if (response == null || response.getCode() == null || response.getCode() != 200 || response.getData() == null) {
            throw new IllegalStateException("failed to fetch profile overview");
        }
        return response.getData();
    }
}
