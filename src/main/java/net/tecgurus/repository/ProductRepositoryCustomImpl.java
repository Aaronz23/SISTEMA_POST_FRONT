package net.tecgurus.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Repository
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public long convertAllPricesToDecimal() {
        MongoCollection<Document> collection = mongoTemplate.getCollection("products");

        Bson filter = new Document();

        List<Bson> pipeline = Collections.singletonList(
            new Document("$set", new Document("price",
                new Document("$convert", new Document("input", "$price")
                    .append("to", "decimal")
                    .append("onError", BigDecimal.ZERO)
                    .append("onNull", BigDecimal.ZERO)
                )
            ))
        );

        UpdateResult result = collection.updateMany(filter, pipeline);
        return result.getModifiedCount();
    }
}
