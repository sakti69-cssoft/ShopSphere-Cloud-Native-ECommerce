package com.shopsphere;
import java.util.*;import java.util.concurrent.ConcurrentHashMap;
public interface UserRepository {Optional<User> findByEmail(String email);Optional<User> findById(UUID id);User save(User user);
 class InMemory implements UserRepository{private final Map<UUID,User> data=new ConcurrentHashMap<>();public Optional<User> findByEmail(String email){return data.values().stream().filter(u->u.email().equalsIgnoreCase(email)).findFirst();}public Optional<User> findById(UUID id){return Optional.ofNullable(data.get(id));}public User save(User u){data.put(u.id(),u);return u;}}
}
