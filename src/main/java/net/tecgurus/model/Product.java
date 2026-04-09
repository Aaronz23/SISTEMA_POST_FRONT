package net.tecgurus.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    private String key;
    private String name;
    private String productLine;
    private Date lastSaleDate;
    @Field("price")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal price;
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDateTime createdAt;
}
