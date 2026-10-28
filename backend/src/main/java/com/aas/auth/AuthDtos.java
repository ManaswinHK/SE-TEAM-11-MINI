package com.aas.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record VerifyOtpRequest(@NotBlank String challengeToken,
                                   @NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit code") String code) {
    }

    public record MfaEnrollment(String secret, String otpauthUrl) {
    }

    public record UserView(Long id, String email, String fullName, Role role, Long departmentId, Long orgId, String orgName) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(boolean mfaRequired, String challengeToken, MfaEnrollment enrollment, String devOtp,
                                String accessToken, Long expiresIn, UserView user) {

        static LoginResponse challenge(String challengeToken, MfaEnrollment enrollment, String devOtp) {
            return new LoginResponse(true, challengeToken, enrollment, devOtp, null, null, null);
        }

        static LoginResponse session(String accessToken, long expiresIn, UserView user) {
            return new LoginResponse(false, null, null, null, accessToken, expiresIn, user);
        }
    }
}
