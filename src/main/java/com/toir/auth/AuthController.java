package com.toir.auth;

import com.toir.auth.dto.LoginRequest;
import com.toir.auth.dto.LoginResponse;
import com.toir.common.security.AuthenticatedUser;
import com.toir.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
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
