package net.tecgurus.repository;

import net.tecgurus.model.Quote;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface QuoteRepository extends MongoRepository<Quote, String> {

    List<Quote> findByUserId(String userId);
    List<Quote> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}