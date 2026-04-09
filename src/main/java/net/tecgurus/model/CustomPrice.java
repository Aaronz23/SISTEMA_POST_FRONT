package net.tecgurus.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@Document(collection = "custom_prices")
public class CustomPrice {

    @Id
    private String id;
    private String userId;
    private String productId;
    private BigDecimal customPrice;
    private BigDecimal profitMargin;
    private String productName;
    private BigDecimal standardPrice;
}
