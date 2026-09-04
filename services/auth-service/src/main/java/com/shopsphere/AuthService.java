package com.shopsphere;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JwtService jwt;

  public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
    this.users = users;
    this.passwords = passwords;
    this.jwt = jwt;
  }

  @Transactional
  public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
    String email = email(request.email());
    if (users.findByEmail(email).isPresent()) throw new ConflictException("Email is already registered");
    Instant now = Instant.now();
    User user = new User(UUID.randomUUID(), clean(request.firstName()), clean(request.lastName()), email,
        passwords.encode(request.password()), phone(request.phone()), User.Role.CUSTOMER, true, now, now);
    return AuthDtos.UserResponse.from(users.save(user));
  }

  public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
    User user = users.findByEmail(request.email()).filter(User::enabled)
        .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    if (!passwords.matches(request.password(), user.passwordHash())) {
      throw new UnauthorizedException("Invalid email or password");
    }
    return tokens(user);
  }

  public AuthDtos.TokenResponse refresh(String token) {
    Jwt parsed = jwt.validate(token, "refresh");
    User user = users.findById(UUID.fromString(parsed.getSubject()))
        .filter(User::enabled).orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    return tokens(user);
  }

  public AuthDtos.UserResponse me(UUID id) {
    return AuthDtos.UserResponse.from(user(id));
  }

  @Transactional
  public AuthDtos.UserResponse updateProfile(UUID id, AuthDtos.UpdateProfileRequest request) {
    User current = user(id);
    String email = email(request.email());
    users.findByEmail(email).filter(other -> !other.id().equals(id)).ifPresent(other -> {
      throw new ConflictException("Email is already registered");
    });
    User updated = new User(current.id(), clean(request.firstName()), clean(request.lastName()), email,
        current.passwordHash(), phone(request.phone()), current.role(), current.enabled(), current.createdAt(),
        Instant.now());
    return AuthDtos.UserResponse.from(users.save(updated));
  }

  @Transactional
  public void changePassword(UUID id, AuthDtos.ChangePasswordRequest request) {
    User current = user(id);
    if (!passwords.matches(request.currentPassword(), current.passwordHash())) {
      throw new UnauthorizedException("Current password is incorrect");
    }
    if (passwords.matches(request.newPassword(), current.passwordHash())) {
      throw new BadRequestException("New password must be different from the current password");
    }
    users.save(new User(current.id(), current.firstName(), current.lastName(), current.email(),
        passwords.encode(request.newPassword()), current.phone(), current.role(), current.enabled(),
        current.createdAt(), Instant.now()));
  }

  private User user(UUID id) {
    return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
  }

  private AuthDtos.TokenResponse tokens(User user) {
    return new AuthDtos.TokenResponse(jwt.access(user), jwt.refresh(user), "Bearer", jwt.accessSeconds(),
        AuthDtos.UserResponse.from(user));
  }

  private String email(String value) {
    return clean(value).toLowerCase(Locale.ROOT);
  }

  private String clean(String value) {
    return value.trim().replaceAll("\\s+", " ");
  }

  private String phone(String value) {
    String cleaned = clean(Objects.requireNonNullElse(value, ""));
    return cleaned.isEmpty() ? null : cleaned;
  }
}
