package com.toir.controller;
import com.toir.service.AuthService;

import com.toir.dto.auth.LoginRequest;
import com.toir.dto.auth.LoginResponse;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthenticatedUser me(@CurrentUser AuthenticatedUser user) {
        return user;
    }
}
