package com.shopsphere;
import jakarta.validation.Valid;import java.util.UUID;import org.springframework.http.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") public class AuthController {private final AuthService auth;public AuthController(AuthService auth){this.auth=auth;}
 @PostMapping("/register")@ResponseStatus(HttpStatus.CREATED)public AuthDtos.UserResponse register(@Valid @RequestBody AuthDtos.RegisterRequest r){return auth.register(r);}
 @PostMapping("/login")public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest r){return auth.login(r);}
 @PostMapping("/refresh")public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest r){return auth.refresh(r.refreshToken());}
 @GetMapping("/me")public AuthDtos.UserResponse me(@AuthenticationPrincipal Jwt jwt){return auth.me(UUID.fromString(jwt.getSubject()));}
}
