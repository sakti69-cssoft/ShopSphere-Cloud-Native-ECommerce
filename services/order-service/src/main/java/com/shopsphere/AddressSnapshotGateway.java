package com.shopsphere;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

interface AddressSnapshotGateway {
  Address resolve(UUID userId, UUID addressId, String authorization);
}

class HttpAddressSnapshotGateway implements AddressSnapshotGateway {
  private final RestClient client;

  HttpAddressSnapshotGateway(RestClient client) {
    this.client = client;
  }

  public Address resolve(UUID userId, UUID addressId, String authorization) {
    if (authorization == null || authorization.isBlank()) throw new AddressResolutionException("Authentication is required");
    try {
      AddressResponse address = client.get().uri("/api/auth/addresses/{id}", addressId)
          .header(HttpHeaders.AUTHORIZATION, authorization).retrieve()
          .onStatus(HttpStatusCode::isError, (request, response) -> {
            throw new AddressResolutionException("Saved address is unavailable");
          }).body(AddressResponse.class);
      if (address == null) throw new AddressResolutionException("Saved address is unavailable");
      return new Address(address.recipientName(), address.line1(), address.line2(), address.city(), address.state(),
          address.postalCode(), address.country(), address.phone());
    } catch (AddressResolutionException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new AddressResolutionException("Address service is temporarily unavailable");
    }
  }

  private record AddressResponse(UUID id, String recipientName, String phone, String line1, String line2,
      String city, String state, String postalCode, String country, boolean defaultAddress) {}
}

class AddressResolutionException extends RuntimeException {
  AddressResolutionException(String message) { super(message); }
}
