package net.tecgurus.controller.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.tecgurus.model.Product;

import java.util.List;

@Data
@AllArgsConstructor
public class ImportResult {
    private String importLogId;
    private List<Product> savedProducts;
    private int updatedQuotesCount;
}
