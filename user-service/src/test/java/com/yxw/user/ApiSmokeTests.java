package com.yxw.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testHelloEndpointReturnsWrappedResponse() throws Exception {
        mockMvc.perform(get("/api/test/hello").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Hello NutriMind backend"));
    }

    @Test
    void loginValidationReturnsUnifiedErrorResponse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").exists());
    }

    @Test
    void registerEndpointReturnsToken() throws Exception {
        String username = uniqueUsername();
        String payload = registerPayload(username, "Abc12345", "测试用户", username + "@example.com", uniquePhone());

        mockMvc.perform(post("/api/auth/register")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.userId").exists());
    }

    @Test
    void checkUsernameEndpointReflectsAvailability() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(get("/api/auth/check-username")
                        .contextPath("/api")
                        .param("username", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.normalizedUsername").value(username));

        mockMvc.perform(post("/api/auth/register")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(username, "Abc12345", "tester", username + "@example.com", uniquePhone())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/auth/check-username")
                        .contextPath("/api")
                        .param("username", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.message").value("username already exists"));
    }

    @Test
    void registerThenLoginWithEmailSucceeds() throws Exception {
        String username = uniqueUsername();
        String email = username + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(username, "Abc12345", "tester", email, uniquePhone())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String loginPayload = """
                {
                  "username": "%s",
                  "password": "Abc12345"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String firstUsername = uniqueUsername();
        String duplicateEmail = firstUsername + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(firstUsername, "Abc12345", "tester", duplicateEmail, uniquePhone())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/auth/register")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(uniqueUsername(), "Abc12345", "tester2", duplicateEmail, uniquePhone())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("email already exists"));
    }

    @Test
    void currentUserEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me").contextPath("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void profileEndpointsPersistHealthProfileAndGoal() throws Exception {
        String username = uniqueUsername();
        String token = registerAndExtractToken(username);

        mockMvc.perform(put("/api/profile/health")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gender": "MALE",
                                  "birthDate": "2000-01-02",
                                  "heightCm": 175.5,
                                  "activityLevel": "MEDIUM",
                                  "dietaryPreference": "high protein",
                                  "allergies": "none",
                                  "medicalNotes": "smoke test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(put("/api/profile/goal")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goalType": "FAT_LOSS",
                                  "targetCalories": 1800,
                                  "targetProtein": 130,
                                  "targetFat": 55,
                                  "targetCarbohydrate": 180,
                                  "targetWeightKg": 68,
                                  "weeklyChangeKg": -0.5,
                                  "startDate": "2026-03-24",
                                  "endDate": "2026-05-24",
                                  "note": "smoke test goal"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/profile/overview")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.healthProfile.gender").value("MALE"))
                .andExpect(jsonPath("$.data.healthProfile.birthDate").value("2000-01-02"))
                .andExpect(jsonPath("$.data.healthProfile.heightCm").value(175.5))
                .andExpect(jsonPath("$.data.healthGoal.goalType").value("FAT_LOSS"))
                .andExpect(jsonPath("$.data.healthGoal.targetCalories").value(1800))
                .andExpect(jsonPath("$.data.healthGoal.weeklyChangeKg").value(-0.5))
                .andExpect(jsonPath("$.data.healthGoal.endDate").value("2026-05-24"));
    }

    private String registerPayload(String username,
                                   String password,
                                   String nickname,
                                   String email,
                                   String phone) {
        return """
                {
                  "username": "%s",
                  "password": "%s",
                  "confirmPassword": "%s",
                  "nickname": "%s",
                  "email": "%s",
                  "phone": "%s"
                }
                """.formatted(username, password, password, nickname, email, phone);
    }

    private String registerAndExtractToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(username, "Abc12345", "tester", username + "@example.com", uniquePhone())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private String uniqueUsername() {
        return "user" + System.nanoTime();
    }

    private String uniquePhone() {
        return "13%09d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000L));
    }
}
