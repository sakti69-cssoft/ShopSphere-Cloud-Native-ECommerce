package com.shopsphere;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.util.*;
public final class OrderDtos{
 private OrderDtos(){}
 // Legacy fields are accepted for compatibility but never used in calculations.
 public record Item(@NotNull UUID productId,String productName,String sku,BigDecimal unitPrice,@Min(1) @Max(100) int quantity){}
 public record AddressRequest(@NotBlank String recipient,@NotBlank String line1,String line2,@NotBlank String city,@NotBlank String state,@NotBlank String postalCode,@NotBlank String country,@NotBlank String phone){}
 public record Create(@NotNull UUID userId,@NotEmpty @Size(max=100) List<@Valid Item>items,BigDecimal discount,BigDecimal deliveryFee,UUID addressId,@Pattern(regexp="^[A-Za-z0-9_-]{3,32}$") String couponCode,@Valid AddressRequest shippingAddress){
  public Create(UUID userId,List<Item>items,BigDecimal discount,BigDecimal deliveryFee,AddressRequest shippingAddress){this(userId,items,discount,deliveryFee,null,null,shippingAddress);}
 }
 public record StatusUpdate(@NotNull Order.Status status){}
}
