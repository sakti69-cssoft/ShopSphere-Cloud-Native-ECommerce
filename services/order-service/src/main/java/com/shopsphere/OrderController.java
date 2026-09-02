package com.shopsphere;
import jakarta.validation.Valid;import java.util.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/orders")
public class OrderController{
 private final OrderService service;OrderController(OrderService s){service=s;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("#r.userId().toString() == authentication.name")
 Order create(@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody OrderDtos.Create r){return service.create(key,r);}
 @GetMapping @PreAuthorize("hasRole('ADMIN')")
 List<Order> all(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size){return service.all(page,size);}
 @GetMapping("/{id}") @PreAuthorize("hasRole('ADMIN') or @orderService.get(#id).userId().toString() == authentication.name")
 Order get(@PathVariable UUID id){return service.get(id);}
 @GetMapping("/user/{userId}") @PreAuthorize("hasRole('ADMIN') or #userId.toString() == authentication.name")
 List<Order> user(@PathVariable UUID userId){return service.user(userId);}
 @PutMapping("/{id}/status") @PreAuthorize("hasRole('ADMIN')")
 Order status(@PathVariable UUID id,@Valid @RequestBody OrderDtos.StatusUpdate r){return service.status(id,r.status());}
 @PostMapping("/{id}/cancel") @PreAuthorize("hasRole('ADMIN') or @orderService.get(#id).userId().toString() == authentication.name")
 Order cancel(@PathVariable UUID id){return service.cancel(id);}
}
