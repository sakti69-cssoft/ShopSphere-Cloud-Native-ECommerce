package com.shopsphere;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {
 private record Totals(List<OrderItem>items,BigDecimal subtotal,CouponService.Discount coupon,BigDecimal delivery,BigDecimal total){}
 private final OrderRepository repo;
 private final InventoryGateway inventory;
 private final OrderIdempotencyStore idempotency;
 private final ProductPricingGateway pricing;
 private final AddressSnapshotGateway addresses;
 private final CouponService coupons;
 private final TransactionTemplate transaction;
 @Autowired public OrderService(OrderRepository repo, InventoryGateway inventory, OrderIdempotencyStore idempotency, ProductPricingGateway pricing,AddressSnapshotGateway addresses,CouponService coupons, PlatformTransactionManager manager) {
  this.repo=repo;this.inventory=inventory;this.idempotency=idempotency;this.pricing=pricing;this.addresses=addresses;this.coupons=coupons;
  this.transaction=new TransactionTemplate(manager);
 }
 OrderService(OrderRepository repo, InventoryGateway inventory, OrderIdempotencyStore idempotency, ProductPricingGateway pricing) {
  this(repo,inventory,idempotency,pricing,(user,address,authorization)->{throw new AddressResolutionException("Saved address is unavailable");},new CouponService(new CouponStore.InMemory()));
 }
 OrderService(OrderRepository repo,InventoryGateway inventory,OrderIdempotencyStore idempotency,ProductPricingGateway pricing,AddressSnapshotGateway addresses,CouponService coupons){
  this.repo=repo;this.inventory=inventory;this.idempotency=idempotency;this.pricing=pricing;this.addresses=addresses;this.coupons=coupons;this.transaction=null;
 }
 public CouponDtos.QuoteResponse quote(CouponDtos.QuoteRequest request){var totals=totals(request.userId(),request.items(),request.couponCode());return new CouponDtos.QuoteResponse(totals.subtotal(),totals.coupon().amount(),totals.delivery(),totals.total(),totals.coupon().code(),totals.coupon().description());}
 public Order create(String key,OrderDtos.Create request){return create(key,null,request);}
 public Order create(String key,String authorization,OrderDtos.Create request) {
  if(key==null||key.isBlank()||key.length()>160||!key.matches("[A-Za-z0-9._:-]+"))throw new MissingIdempotencyKeyException("Idempotency-Key must be 1-160 safe characters");
  var claim=idempotency.claim(key,request.userId(),fingerprint(request));
  if(claim.state()==OrderIdempotencyStore.State.CONFLICT)throw new IdempotencyConflictException("Idempotency-Key was already used for a different order request");
  if(claim.state()==OrderIdempotencyStore.State.COMPLETED)return get(claim.orderId());
  if(claim.state()==OrderIdempotencyStore.State.PROCESSING)throw new IdempotencyInProgressException("Order is processing; retry with the same key");
  List<OrderItem> reserved=new ArrayList<>();
  try {
   var totals=totals(request.userId(),request.items(),request.couponCode());
   for(var item:totals.items()){inventory.reserve(item.productId(),item.quantity());reserved.add(item);}
   var address=shippingAddress(request,authorization);Instant now=Instant.now();
   Order order=new Order(UUID.randomUUID(),orderNumber(),request.userId(),totals.items(),totals.subtotal(),totals.coupon().amount(),totals.coupon().code(),totals.delivery(),totals.total(),Order.Status.CONFIRMED,Order.PaymentStatus.PENDING,address,now,now);
   // The order and completed result commit atomically in the same service-owned MySQL database.
   if(transaction!=null)return transaction.execute(status->persist(key,order));
   return persist(key,order);
  } catch(RuntimeException failure) {
   for(var item:reserved){try{inventory.release(item.productId(),item.quantity());}catch(RuntimeException compensation){failure.addSuppressed(compensation);}}
   idempotency.release(key);throw failure;
  }
 }
 private Totals totals(UUID userId,List<OrderDtos.Item>requested,String couponCode){
  List<OrderItem>items=requested.stream().map(item->{var product=pricing.resolve(item);if(!product.active()||!product.id().equals(item.productId())||product.price()==null||product.price().signum()<=0)throw new BusinessException("Product is not purchasable");return new OrderItem(product.id(),product.name(),product.sku(),product.price(),item.quantity(),product.price().multiply(BigDecimal.valueOf(item.quantity())));}).toList();
  BigDecimal subtotal=items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO,BigDecimal::add);
  var discount=coupons.quote(userId,couponCode,subtotal);
  BigDecimal delivery=subtotal.compareTo(BigDecimal.valueOf(999))>0?BigDecimal.ZERO:BigDecimal.valueOf(99);
  return new Totals(items,subtotal,discount,delivery,subtotal.subtract(discount.amount()).add(delivery).max(BigDecimal.ZERO));
 }
 private Address shippingAddress(OrderDtos.Create request,String authorization){
  if(request.addressId()!=null)return addresses.resolve(request.userId(),request.addressId(),authorization);
  if(request.shippingAddress()==null)throw new AddressResolutionException("Select a saved shipping address");
  var address=request.shippingAddress();return new Address(address.recipient(),address.line1(),address.line2(),address.city(),address.state(),address.postalCode(),address.country(),address.phone());
 }
 private Order persist(String key,Order order){Order saved=repo.save(order);coupons.redeem(saved.userId(),saved.id(),saved.couponCode(),saved.subtotal(),saved.discount());idempotency.complete(key,saved.id());return saved;}
 public Order get(UUID id){return repo.find(id).orElseThrow(()->new NotFoundException("Order not found"));}
 public List<Order> user(UUID id){return repo.findByUser(id);}
 public List<Order> all(int page,int size){return repo.findAll(page,Math.min(Math.max(size,1),100));}
 public Order status(UUID id,Order.Status next){if(next==Order.Status.CANCELLED)return cancel(id);Order o=get(id);if(!allowed(o.status(),next))throw new BusinessException("Invalid order status transition");return save(o,next,o.paymentStatus());}
 public Order cancel(UUID id){Order o=get(id);if(Set.of(Order.Status.SHIPPED,Order.Status.DELIVERED,Order.Status.CANCELLED).contains(o.status()))throw new BusinessException("Order can no longer be cancelled");o.items().forEach(i->inventory.release(i.productId(),i.quantity()));return save(o,Order.Status.CANCELLED,o.paymentStatus()==Order.PaymentStatus.SUCCESS?Order.PaymentStatus.REFUNDED:o.paymentStatus());}
 private String orderNumber(){return "SS-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);}
 private String fingerprint(OrderDtos.Create r){
  // Ignore legacy financial fields: changing an untrusted price does not alter the customer's order intent.
  StringBuilder b=new StringBuilder(r.userId().toString());
  r.items().stream().sorted(Comparator.comparing(i->i.productId().toString())).forEach(i->b.append('|').append(i.productId()).append(':').append(i.quantity()));
  b.append("|coupon:").append(Objects.requireNonNullElse(r.couponCode(),"").trim().toUpperCase(Locale.ROOT));
  b.append("|addressId:").append(Objects.requireNonNullElse(r.addressId(),""));
  if(r.addressId()==null&&r.shippingAddress()!=null){var a=r.shippingAddress();for(String s:Arrays.asList(a.recipient(),a.line1(),a.line2(),a.city(),a.state(),a.postalCode(),a.country(),a.phone()))b.append('|').append(Objects.requireNonNullElse(s,"").trim());}
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.toString().getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private boolean allowed(Order.Status a,Order.Status b){return switch(a){case PENDING->b==Order.Status.CONFIRMED;case CONFIRMED->b==Order.Status.PROCESSING;case PROCESSING->b==Order.Status.SHIPPED;case SHIPPED->b==Order.Status.DELIVERED;default->false;};}
 private Order save(Order o,Order.Status s,Order.PaymentStatus p){return repo.save(new Order(o.id(),o.orderNumber(),o.userId(),o.items(),o.subtotal(),o.discount(),o.couponCode(),o.deliveryFee(),o.totalAmount(),s,p,o.shippingAddress(),o.createdAt(),Instant.now()));}
}
