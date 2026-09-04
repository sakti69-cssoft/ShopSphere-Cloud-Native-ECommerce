package com.shopsphere;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
  private static final String NAME_PATTERN = "^[\\p{L}][\\p{L} .'-]{0,79}$";
  private static final String PHONE_PATTERN = "^[+0-9 ()-]{7,20}$";

  private AuthDtos() {}

  public record RegisterRequest(
      @NotBlank @Pattern(regexp = NAME_PATTERN) String firstName,
      @NotBlank @Pattern(regexp = NAME_PATTERN) String lastName,
      @Email @NotBlank @Size(max = 320) String email,
      @Size(min = 8, max = 72) String password,
      @Pattern(regexp = PHONE_PATTERN) String phone) {}

  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record UpdateProfileRequest(
      @NotBlank @Pattern(regexp = NAME_PATTERN) String firstName,
      @NotBlank @Pattern(regexp = NAME_PATTERN) String lastName,
      @Email @NotBlank @Size(max = 320) String email,
      @Pattern(regexp = PHONE_PATTERN) String phone) {}

  public record ChangePasswordRequest(
      @NotBlank String currentPassword,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public record UserResponse(
      UUID id,
      String firstName,
      String lastName,
      String email,
      String phone,
      User.Role role,
      boolean enabled,
      Instant createdAt,
      Instant updatedAt) {
    static UserResponse from(User u) {
      return new UserResponse(
          u.id(), u.firstName(), u.lastName(), u.email(), u.phone(), u.role(), u.enabled(), u.createdAt(), u.updatedAt());
    }
  }

  public record TokenResponse(
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresInSeconds,
      UserResponse user) {}
}
