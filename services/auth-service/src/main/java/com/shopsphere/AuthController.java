package com.shopsphere;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthDtos.UserResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
    return auth.register(request);
  }

  @PostMapping("/login")
  public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
    return auth.login(request);
  }

  @PostMapping("/refresh")
  public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
    return auth.refresh(request.refreshToken());
  }

  @GetMapping("/me")
  public AuthDtos.UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    return auth.me(userId(jwt));
  }

  @PutMapping("/me")
  public AuthDtos.UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AuthDtos.UpdateProfileRequest request) {
    return auth.updateProfile(userId(jwt), request);
  }

  @PutMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
    auth.changePassword(userId(jwt), request);
  }

  private UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
