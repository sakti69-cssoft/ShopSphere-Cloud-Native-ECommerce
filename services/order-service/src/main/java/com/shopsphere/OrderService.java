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
 private final OrderRepository repo;
 private final InventoryGateway inventory;
 private final OrderIdempotencyStore idempotency;
 private final ProductPricingGateway pricing;
 private final TransactionTemplate transaction;
 @Autowired public OrderService(OrderRepository repo, InventoryGateway inventory, OrderIdempotencyStore idempotency, ProductPricingGateway pricing, PlatformTransactionManager manager) {
  this.repo=repo; this.inventory=inventory; this.idempotency=idempotency; this.pricing=pricing;
  this.transaction=new TransactionTemplate(manager);
 }
 OrderService(OrderRepository repo, InventoryGateway inventory, OrderIdempotencyStore idempotency, ProductPricingGateway pricing) {
  this.repo=repo;this.inventory=inventory;this.idempotency=idempotency;this.pricing=pricing;this.transaction=null;
 }
 public Order create(String key, OrderDtos.Create request) {
  if(key==null||key.isBlank()||key.length()>160||!key.matches("[A-Za-z0-9._:-]+"))throw new MissingIdempotencyKeyException("Idempotency-Key must be 1-160 safe characters");
  var claim=idempotency.claim(key,request.userId(),fingerprint(request));
  if(claim.state()==OrderIdempotencyStore.State.CONFLICT)throw new IdempotencyConflictException("Idempotency-Key was already used for a different order request");
  if(claim.state()==OrderIdempotencyStore.State.COMPLETED)return get(claim.orderId());
  if(claim.state()==OrderIdempotencyStore.State.PROCESSING)throw new IdempotencyInProgressException("Order is processing; retry with the same key");
  List<OrderItem> reserved=new ArrayList<>();
  try {
   // Only productId and quantity are trusted as customer intent. Catalog owns all financial/product fields.
   List<OrderItem> items=request.items().stream().map(i->{
    var product=pricing.resolve(i);
    if(!product.active()||!product.id().equals(i.productId())||product.price()==null||product.price().signum()<=0)throw new BusinessException("Product is not purchasable");
    return new OrderItem(product.id(),product.name(),product.sku(),product.price(),i.quantity(),product.price().multiply(BigDecimal.valueOf(i.quantity())));
   }).toList();
   BigDecimal subtotal=items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO,BigDecimal::add);
   BigDecimal discount=BigDecimal.ZERO;
   BigDecimal delivery=subtotal.compareTo(BigDecimal.valueOf(999))>0?BigDecimal.ZERO:BigDecimal.valueOf(99);
   for(var item:items){inventory.reserve(item.productId(),item.quantity());reserved.add(item);}
   var a=request.shippingAddress();Instant now=Instant.now();
   Order order=new Order(UUID.randomUUID(),orderNumber(),request.userId(),items,subtotal,discount,delivery,subtotal.add(delivery),Order.Status.CONFIRMED,Order.PaymentStatus.PENDING,new Address(a.recipient(),a.line1(),a.line2(),a.city(),a.state(),a.postalCode(),a.country(),a.phone()),now,now);
   // The order and completed result commit atomically in the same service-owned MySQL database.
   if(transaction!=null)return transaction.execute(status->persist(key,order));
   return persist(key,order);
  } catch(RuntimeException failure) {
   for(var item:reserved){try{inventory.release(item.productId(),item.quantity());}catch(RuntimeException compensation){failure.addSuppressed(compensation);}}
   idempotency.release(key);throw failure;
  }
 }
 private Order persist(String key,Order order){Order saved=repo.save(order);idempotency.complete(key,saved.id());return saved;}
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
  var a=r.shippingAddress();
  for(String s:Arrays.asList(a.recipient(),a.line1(),a.line2(),a.city(),a.state(),a.postalCode(),a.country(),a.phone()))b.append('|').append(Objects.requireNonNullElse(s,"").trim());
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.toString().getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private boolean allowed(Order.Status a,Order.Status b){return switch(a){case PENDING->b==Order.Status.CONFIRMED;case CONFIRMED->b==Order.Status.PROCESSING;case PROCESSING->b==Order.Status.SHIPPED;case SHIPPED->b==Order.Status.DELIVERED;default->false;};}
 private Order save(Order o,Order.Status s,Order.PaymentStatus p){return repo.save(new Order(o.id(),o.orderNumber(),o.userId(),o.items(),o.subtotal(),o.discount(),o.deliveryFee(),o.totalAmount(),s,p,o.shippingAddress(),o.createdAt(),Instant.now()));}
}
