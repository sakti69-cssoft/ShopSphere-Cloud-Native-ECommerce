package com.shopsphere;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressServiceTest {
  private AddressRepository.InMemory repository;
  private AddressService service;
  private UUID user;

  @BeforeEach
  void setUp() {
    repository = new AddressRepository.InMemory();
    service = new AddressService(repository);
    user = UUID.randomUUID();
  }

  @Test
  void addsPersistsAndUpdatesAnAddress() {
    var created = service.add(user, request("Aarav Mehta", true, "1 Main Street"));
    assertTrue(created.defaultAddress());
    assertEquals(1, service.list(user).size());
    var updated = service.update(user, created.id(), request("Aarav M", false, "2 Market Road"));
    assertEquals("2 Market Road", updated.line1());
    assertTrue(updated.defaultAddress());
  }

  @Test
  void maintainsExactlyOneDefaultAddress() {
    var first = service.add(user, request("First", false, "1 Main Street"));
    var second = service.add(user, request("Second", true, "2 Main Street"));
    var rows = service.list(user);
    assertEquals(1, rows.stream().filter(AddressDtos.AddressResponse::defaultAddress).count());
    assertEquals(second.id(), rows.getFirst().id());
    assertFalse(service.get(user, first.id()).defaultAddress());
  }

  @Test
  void deletingDefaultPromotesOldestRemainingAddress() {
    var first = service.add(user, request("First", false, "1 Main Street"));
    var second = service.add(user, request("Second", false, "2 Main Street"));
    service.delete(user, first.id());
    assertTrue(service.get(user, second.id()).defaultAddress());
  }

  @Test
  void preventsCrossCustomerReadUpdateAndDelete() {
    var address = service.add(user, request("Owner", false, "1 Main Street"));
    UUID other = UUID.randomUUID();
    assertThrows(NotFoundException.class, () -> service.get(other, address.id()));
    assertThrows(NotFoundException.class, () -> service.update(other, address.id(), request("Other", false, "2 Main Street")));
    assertThrows(NotFoundException.class, () -> service.delete(other, address.id()));
    assertEquals(1, service.list(user).size());
  }

  private AddressDtos.AddressRequest request(String recipient, boolean makeDefault, String line1) {
    return new AddressDtos.AddressRequest(recipient, "+91 9876543210", line1, null, "Bengaluru",
        "Karnataka", "560001", "India", makeDefault);
  }
}
