package net.tecgurus.repository;

import net.tecgurus.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends MongoRepository<Product, String>, ProductRepositoryCustom {

    Product findByKey(String key);
    void deleteByKey(String key);
    List<Product> findByKeyIn(List<String> keys);

    @Query(value = "{}", fields = "{'price' : 1, '_id' : 0}")
    List<Product> findAllPrices();
}
