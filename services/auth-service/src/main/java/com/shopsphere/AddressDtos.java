package com.shopsphere;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AddressDtos {
  private AddressDtos() {}

  public record AddressRequest(
      @NotBlank @Size(max = 120) String recipientName,
      @NotBlank @Pattern(regexp = "^[+0-9 ()-]{7,20}$") String phone,
      @NotBlank @Size(max = 255) String line1,
      @Size(max = 255) String line2,
      @NotBlank @Pattern(regexp = "^[\\p{L} .'-]{2,100}$") String city,
      @NotBlank @Pattern(regexp = "^[\\p{L} .'-]{2,100}$") String state,
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{3,20}$") String postalCode,
      @NotBlank @Pattern(regexp = "^[\\p{L} .'-]{2,100}$") String country,
      boolean defaultAddress) {}

  public record AddressResponse(
      UUID id,
      String recipientName,
      String phone,
      String line1,
      String line2,
      String city,
      String state,
      String postalCode,
      String country,
      boolean defaultAddress,
      Instant createdAt,
      Instant updatedAt) {
    static AddressResponse from(CustomerAddress address) {
      return new AddressResponse(
          address.id(), address.recipientName(), address.phone(), address.line1(), address.line2(),
          address.city(), address.state(), address.postalCode(), address.country(),
          address.defaultAddress(), address.createdAt(), address.updatedAt());
    }
  }
}
