package com.shopsphere;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AddressService {
  private static final int MAX_ADDRESSES = 10;
  private final AddressRepository addresses;

  AddressService(AddressRepository addresses) {
    this.addresses = addresses;
  }

  @Transactional(readOnly = true)
  List<AddressDtos.AddressResponse> list(UUID userId) {
    return addresses.findByUser(userId).stream().map(AddressDtos.AddressResponse::from).toList();
  }

  @Transactional(readOnly = true)
  AddressDtos.AddressResponse get(UUID userId, UUID id) {
    return AddressDtos.AddressResponse.from(owned(userId, id));
  }

  @Transactional
  AddressDtos.AddressResponse add(UUID userId, AddressDtos.AddressRequest request) {
    var existing = addresses.findByUser(userId);
    if (existing.size() >= MAX_ADDRESSES) throw new BadRequestException("A customer may save up to 10 addresses");
    boolean makeDefault = request.defaultAddress() || existing.isEmpty();
    if (makeDefault) addresses.clearDefault(userId);
    Instant now = Instant.now();
    return AddressDtos.AddressResponse.from(addresses.save(address(UUID.randomUUID(), userId, request, makeDefault, now, now)));
  }

  @Transactional
  AddressDtos.AddressResponse update(UUID userId, UUID id, AddressDtos.AddressRequest request) {
    CustomerAddress current = owned(userId, id);
    boolean makeDefault = request.defaultAddress() || current.defaultAddress();
    if (makeDefault) addresses.clearDefault(userId);
    return AddressDtos.AddressResponse.from(addresses.save(address(id, userId, request, makeDefault,
        current.createdAt(), Instant.now())));
  }

  @Transactional
  AddressDtos.AddressResponse setDefault(UUID userId, UUID id) {
    CustomerAddress current = owned(userId, id);
    addresses.clearDefault(userId);
    CustomerAddress updated = new CustomerAddress(current.id(), current.userId(), current.recipientName(),
        current.phone(), current.line1(), current.line2(), current.city(), current.state(), current.postalCode(),
        current.country(), true, current.createdAt(), Instant.now());
    return AddressDtos.AddressResponse.from(addresses.save(updated));
  }

  @Transactional
  void delete(UUID userId, UUID id) {
    CustomerAddress current = owned(userId, id);
    addresses.delete(current);
    if (current.defaultAddress()) {
      addresses.findByUser(userId).stream().findFirst().ifPresent(next -> addresses.save(new CustomerAddress(
          next.id(), next.userId(), next.recipientName(), next.phone(), next.line1(), next.line2(), next.city(),
          next.state(), next.postalCode(), next.country(), true, next.createdAt(), Instant.now())));
    }
  }

  private CustomerAddress owned(UUID userId, UUID id) {
    return addresses.findOwned(id, userId).orElseThrow(() -> new NotFoundException("Address not found"));
  }

  private CustomerAddress address(UUID id, UUID userId, AddressDtos.AddressRequest request,
      boolean defaultAddress, Instant createdAt, Instant updatedAt) {
    return new CustomerAddress(id, userId, clean(request.recipientName()), clean(request.phone()),
        clean(request.line1()), nullable(request.line2()), clean(request.city()), clean(request.state()),
        clean(request.postalCode()).toUpperCase(), clean(request.country()), defaultAddress, createdAt, updatedAt);
  }

  private String clean(String value) {
    return value.trim().replaceAll("\\s+", " ");
  }

  private String nullable(String value) {
    String cleaned = clean(Objects.requireNonNullElse(value, ""));
    return cleaned.isEmpty() ? null : cleaned;
  }
}
