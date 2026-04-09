package net.tecgurus.repository;

import net.tecgurus.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByUserId(String userId);
    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);


}