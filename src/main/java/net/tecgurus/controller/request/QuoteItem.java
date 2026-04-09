package net.tecgurus.controller.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class QuoteItem {
    private String productId;
    private int quantity;
    private String key;
    private String name;
    private BigDecimal unitPrice;
}