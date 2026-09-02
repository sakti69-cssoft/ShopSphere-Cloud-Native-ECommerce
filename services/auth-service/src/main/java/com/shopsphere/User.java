package com.shopsphere;
import java.time.Instant;import java.util.UUID;
public record User(UUID id,String firstName,String lastName,String email,String passwordHash,String phone,Role role,boolean enabled,Instant createdAt,Instant updatedAt){public enum Role{CUSTOMER,ADMIN}}
