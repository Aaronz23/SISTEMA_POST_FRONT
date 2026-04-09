package net.tecgurus.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import net.tecgurus.controller.request.QuoteItem;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "orders")
public class Order {

    @Id
    private String id;
    private String type;
    private String quoteId;
    private String userId;
    private List<String> productIds;
    private BigDecimal totalPrice;
    private String status; // e.g., "PENDING", "COMPLETED"
    private String number;         // Folio legible del pedido (P-000001)
    private String quoteNumber;    // Folio legible de la cotización (Q-000001)
    private String username;
    private String nombre;
    private List<QuoteItem> items;
    private String deliveryOption;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
}