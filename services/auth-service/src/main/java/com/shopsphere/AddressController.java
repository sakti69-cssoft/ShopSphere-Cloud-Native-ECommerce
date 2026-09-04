package com.shopsphere;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/addresses")
class AddressController {
  private final AddressService service;

  AddressController(AddressService service) {
    this.service = service;
  }

  @GetMapping
  List<AddressDtos.AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
    return service.list(userId(jwt));
  }

  @GetMapping("/{id}")
  AddressDtos.AddressResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.get(userId(jwt), id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  AddressDtos.AddressResponse add(@AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody AddressDtos.AddressRequest request) {
    return service.add(userId(jwt), request);
  }

  @PutMapping("/{id}")
  AddressDtos.AddressResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
      @Valid @RequestBody AddressDtos.AddressRequest request) {
    return service.update(userId(jwt), id, request);
  }

  @PutMapping("/{id}/default")
  AddressDtos.AddressResponse setDefault(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.setDefault(userId(jwt), id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    service.delete(userId(jwt), id);
  }

  private UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
