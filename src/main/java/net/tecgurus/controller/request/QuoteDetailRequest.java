package net.tecgurus.controller.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Data
public class QuoteDetailRequest {
    private String id;
    private Long number;
    private String userId;
    private String username;
    private String key;
    private BigDecimal totalPrice;
    private BigDecimal profitMargin;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
    private Date expiredAt;
    private List<QuoteItem> items;
}
