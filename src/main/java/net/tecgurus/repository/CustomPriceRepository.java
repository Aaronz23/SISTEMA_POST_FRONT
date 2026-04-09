package net.tecgurus.repository;

import net.tecgurus.model.CustomPrice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CustomPriceRepository extends MongoRepository<CustomPrice, String> {
    List<CustomPrice> findByUserId(String userId);

    void deleteByUserId(String userId);
}
