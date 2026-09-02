package com.shopsphere;
import jakarta.validation.constraints.*;import java.time.Instant;import java.util.UUID;
public final class AuthDtos {private AuthDtos(){}
 public record RegisterRequest(@NotBlank String firstName,@NotBlank String lastName,@Email @NotBlank String email,@Size(min=8,max=72) String password,@Pattern(regexp="^[+0-9 ()-]{7,20}$") String phone){}
 public record LoginRequest(@Email @NotBlank String email,@NotBlank String password){}
 public record RefreshRequest(@NotBlank String refreshToken){}
 public record UserResponse(UUID id,String firstName,String lastName,String email,String phone,User.Role role,boolean enabled,Instant createdAt,Instant updatedAt){static UserResponse from(User u){return new UserResponse(u.id(),u.firstName(),u.lastName(),u.email(),u.phone(),u.role(),u.enabled(),u.createdAt(),u.updatedAt());}}
 public record TokenResponse(String accessToken,String refreshToken,String tokenType,long expiresInSeconds,UserResponse user){}
}
