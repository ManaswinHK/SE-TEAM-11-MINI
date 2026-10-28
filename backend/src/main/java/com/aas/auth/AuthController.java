package com.aas.auth;

import com.aas.auth.AuthDtos.LoginRequest;
import com.aas.auth.AuthDtos.LoginResponse;
import com.aas.auth.AuthDtos.UserView;
import com.aas.auth.AuthDtos.VerifyOtpRequest;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.email(), req.password());
    }

    @PostMapping("/verify-otp")
    public LoginResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return auth.verifyOtp(req.challengeToken(), req.code());
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(Authentication authentication) {
        return auth.refresh((Claims) authentication.getCredentials());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        auth.logout((Claims) authentication.getCredentials());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserView me() {
        return auth.me(CurrentUser.get().userId());
    }
}
