package com.shopsphere;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

interface AddressRepository {
  List<CustomerAddress> findByUser(UUID userId);
  Optional<CustomerAddress> findOwned(UUID id, UUID userId);
  CustomerAddress save(CustomerAddress address);
  void delete(CustomerAddress address);
  void clearDefault(UUID userId);

  class InMemory implements AddressRepository {
    private final Map<UUID, CustomerAddress> data = new ConcurrentHashMap<>();

    public List<CustomerAddress> findByUser(UUID userId) {
      return data.values().stream()
          .filter(address -> address.userId().equals(userId))
          .sorted(Comparator.comparing(CustomerAddress::defaultAddress).reversed()
              .thenComparing(CustomerAddress::createdAt))
          .toList();
    }

    public Optional<CustomerAddress> findOwned(UUID id, UUID userId) {
      return Optional.ofNullable(data.get(id)).filter(address -> address.userId().equals(userId));
    }

    public CustomerAddress save(CustomerAddress address) {
      data.put(address.id(), address);
      return address;
    }

    public void delete(CustomerAddress address) {
      data.remove(address.id());
    }

    public void clearDefault(UUID userId) {
      new ArrayList<>(data.values()).stream().filter(address -> address.userId().equals(userId))
          .forEach(address -> data.put(address.id(), new CustomerAddress(
              address.id(), address.userId(), address.recipientName(), address.phone(), address.line1(),
              address.line2(), address.city(), address.state(), address.postalCode(), address.country(),
              false, address.createdAt(), address.updatedAt())));
    }
  }
}
