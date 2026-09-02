package com.shopsphere;
import java.time.Instant;import java.util.*;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.stereotype.Service;
@Service public class AuthService {private final UserRepository users;private final PasswordEncoder passwords;private final JwtService jwt;
 public AuthService(UserRepository users,PasswordEncoder passwords,JwtService jwt){this.users=users;this.passwords=passwords;this.jwt=jwt;}
 public AuthDtos.UserResponse register(AuthDtos.RegisterRequest r){String email=r.email().trim().toLowerCase();if(users.findByEmail(email).isPresent())throw new ConflictException("Email is already registered");Instant now=Instant.now();User u=new User(UUID.randomUUID(),r.firstName().trim(),r.lastName().trim(),email,passwords.encode(r.password()),r.phone(),User.Role.CUSTOMER,true,now,now);return AuthDtos.UserResponse.from(users.save(u));}
 public AuthDtos.TokenResponse login(AuthDtos.LoginRequest r){User u=users.findByEmail(r.email()).filter(User::enabled).orElseThrow(()->new UnauthorizedException("Invalid email or password"));if(!passwords.matches(r.password(),u.passwordHash()))throw new UnauthorizedException("Invalid email or password");return tokens(u);}
 public AuthDtos.TokenResponse refresh(String token){Jwt parsed=jwt.validate(token,"refresh");User u=users.findById(UUID.fromString(parsed.getSubject())).orElseThrow(()->new UnauthorizedException("User no longer exists"));return tokens(u);}
 public AuthDtos.UserResponse me(UUID id){return users.findById(id).map(AuthDtos.UserResponse::from).orElseThrow(()->new NotFoundException("User not found"));}
 private AuthDtos.TokenResponse tokens(User u){return new AuthDtos.TokenResponse(jwt.access(u),jwt.refresh(u),"Bearer",jwt.accessSeconds(),AuthDtos.UserResponse.from(u));}
}
