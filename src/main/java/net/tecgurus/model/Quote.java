package net.tecgurus.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import net.tecgurus.controller.request.QuoteItem;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Data
@Document(collection = "quotes")
public class Quote {

    @Id
    private String id;
    @Indexed(unique = true, sparse = true)
    private Long number;
    private String userId;
    @Field("clientId")
    private String clientId;
    private BigDecimal profitMargin;
    @Field("totalPrice")
    private BigDecimal totalPrice;
    private String status; // e.g., "PENDING", "CONFIRMED"
    private String username;
    private Boolean sendEmail;
    private String recipientEmail;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
    private String nombre;
    private Date expiredAt;

    private List<QuoteItem> items;

}
