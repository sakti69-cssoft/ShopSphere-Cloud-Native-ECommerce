package com.shopsphere;

import java.sql.ResultSet;import java.sql.SQLException;import java.time.Instant;import java.util.*;import org.springframework.dao.DuplicateKeyException;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;

interface OrderIdempotencyStore{
 enum State{NEW,PROCESSING,COMPLETED,CONFLICT}
 record Claim(State state,UUID orderId){}
 Claim claim(String key,UUID userId,String hash);void complete(String key,UUID orderId);void release(String key);
 class InMemory implements OrderIdempotencyStore{private record Entry(UUID user,String hash,UUID order){}private final Map<String,Entry>data=new java.util.concurrent.ConcurrentHashMap<>();public Claim claim(String key,UUID user,String hash){var fresh=new Entry(user,hash,null);var existing=data.putIfAbsent(key,fresh);if(existing==null)return new Claim(State.NEW,null);if(!existing.user.equals(user)||!existing.hash.equals(hash))return new Claim(State.CONFLICT,null);return existing.order==null?new Claim(State.PROCESSING,null):new Claim(State.COMPLETED,existing.order);}public void complete(String key,UUID order){data.computeIfPresent(key,(k,e)->new Entry(e.user,e.hash,order));}public void release(String key){data.remove(key);}}
}

@Repository class JdbcOrderIdempotencyStore implements OrderIdempotencyStore{
 private final JdbcTemplate jdbc;JdbcOrderIdempotencyStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Claim claim(String key,UUID user,String hash){Instant now=Instant.now();try{jdbc.update("INSERT INTO order_idempotency(id,idempotency_key,user_id,request_hash,status,created_at,updated_at) VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),?,?,?,?)",UUID.randomUUID().toString(),key,user.toString(),hash,"PROCESSING",now,now);return new Claim(State.NEW,null);}catch(DuplicateKeyException duplicate){var rows=jdbc.query("SELECT BIN_TO_UUID(user_id) user_id,request_hash,BIN_TO_UUID(order_id) order_id,status FROM order_idempotency WHERE idempotency_key=?",this::map,key);if(rows.isEmpty())throw duplicate;var row=rows.getFirst();if(!row.user.equals(user)||!row.hash.equals(hash))return new Claim(State.CONFLICT,null);return row.order==null?new Claim(State.PROCESSING,null):new Claim(State.COMPLETED,row.order);}}
 public void complete(String key,UUID order){jdbc.update("UPDATE order_idempotency SET order_id=UUID_TO_BIN(?),status='COMPLETED',updated_at=? WHERE idempotency_key=?",order.toString(),Instant.now(),key);}
 public void release(String key){jdbc.update("DELETE FROM order_idempotency WHERE idempotency_key=? AND status='PROCESSING'",key);}
 private Row map(ResultSet rs,int n)throws SQLException{String order=rs.getString("order_id");return new Row(UUID.fromString(rs.getString("user_id")),rs.getString("request_hash"),order==null?null:UUID.fromString(order));}private record Row(UUID user,String hash,UUID order){}
}
