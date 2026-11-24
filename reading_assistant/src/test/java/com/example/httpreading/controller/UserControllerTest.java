package com.example.httpreading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.httpreading.api.CommonResponse;
import com.example.httpreading.domain.user.User;
import com.example.httpreading.dto.AuthRequest;
import com.example.httpreading.security.JwtService;
import com.example.httpreading.service.UserService;

class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final UserController controller = new UserController(userService, jwtService);

    @Test
    void loginReturnsTokenExpirationForClientLifecycle() {
        User user = user(7L, "reader");
        Instant expiresAt = Instant.parse("2026-11-04T12:00:00Z");
        AuthRequest request = new AuthRequest();
        request.setUsername("reader");
        request.setPassword("secret");

        when(userService.authenticate("reader", "secret")).thenReturn(Optional.of(user));
        when(jwtService.issueToken(7L, "reader"))
                .thenReturn(new JwtService.IssuedToken("signed-token", expiresAt));

        CommonResponse<Map<String, Object>> response = controller.login(request);

        assertThat(response.getData()).containsEntry("token", "signed-token");
        assertThat(response.getData()).containsEntry("expiresAt", expiresAt);
    }

    @Test
    void sessionReturnsAuthenticatedUser() {
        when(userService.findById(7L)).thenReturn(Optional.of(user(7L, "reader")));

        CommonResponse<Map<String, Object>> response = controller.session(7L);

        assertThat(response.getData())
                .containsEntry("id", 7L)
                .containsEntry("username", "reader");
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
