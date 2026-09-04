package com.shopsphere;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Entity
@Table(name = "customer_addresses")
class CustomerAddressEntity {
  @Id UUID id;
  @Column(nullable = false) UUID userId;
  @Column(nullable = false) String recipientName;
  @Column(nullable = false) String phone;
  @Column(nullable = false) String line1;
  String line2;
  @Column(nullable = false) String city;
  @Column(nullable = false) String state;
  @Column(nullable = false) String postalCode;
  @Column(nullable = false) String country;
  @Column(name = "is_default", nullable = false) boolean defaultAddress;
  @Column(nullable = false) Instant createdAt;
  @Column(nullable = false) Instant updatedAt;

  protected CustomerAddressEntity() {}

  CustomerAddressEntity(CustomerAddress address) {
    id = address.id();
    userId = address.userId();
    recipientName = address.recipientName();
    phone = address.phone();
    line1 = address.line1();
    line2 = address.line2();
    city = address.city();
    state = address.state();
    postalCode = address.postalCode();
    country = address.country();
    defaultAddress = address.defaultAddress();
    createdAt = address.createdAt();
    updatedAt = address.updatedAt();
  }

  CustomerAddress domain() {
    return new CustomerAddress(id, userId, recipientName, phone, line1, line2, city, state, postalCode,
        country, defaultAddress, createdAt, updatedAt);
  }
}

interface SpringAddressRepository extends JpaRepository<CustomerAddressEntity, UUID> {
  List<CustomerAddressEntity> findByUserIdOrderByDefaultAddressDescCreatedAtAsc(UUID userId);
  Optional<CustomerAddressEntity> findByIdAndUserId(UUID id, UUID userId);

  @Modifying
  @Query("update CustomerAddressEntity address set address.defaultAddress=false where address.userId=:userId and address.defaultAddress=true")
  void clearDefault(@Param("userId") UUID userId);
}

@Repository
class JpaAddressRepository implements AddressRepository {
  private final SpringAddressRepository data;

  JpaAddressRepository(SpringAddressRepository data) {
    this.data = data;
  }

  public List<CustomerAddress> findByUser(UUID userId) {
    return data.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(userId).stream()
        .map(CustomerAddressEntity::domain).toList();
  }

  public Optional<CustomerAddress> findOwned(UUID id, UUID userId) {
    return data.findByIdAndUserId(id, userId).map(CustomerAddressEntity::domain);
  }

  public CustomerAddress save(CustomerAddress address) {
    return data.saveAndFlush(new CustomerAddressEntity(address)).domain();
  }

  public void delete(CustomerAddress address) {
    data.deleteById(address.id());
  }

  public void clearDefault(UUID userId) {
    data.clearDefault(userId);
  }
}
