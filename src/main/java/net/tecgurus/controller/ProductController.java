package net.tecgurus.controller;

import net.tecgurus.controller.response.ProductResponse;
import net.tecgurus.model.Product;
import net.tecgurus.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @GetMapping("/catalog")
    public ResponseEntity<Page<ProductResponse>> getProductCatalog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String productLine,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) String sort
    ) {
        logger.info("Recibida petición de catálogo - priceMin: {}, priceMax: {}", priceMin, priceMax);
        Page<ProductResponse> result = productService.findCatalog(
                page, size, key, name, productLine, priceMin, priceMax, sort
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{key}")
    public ResponseEntity<Product> getProductByKey(@PathVariable String key) {
        Product product = productService.getProductByKey(key);
        if (product != null) {
            return ResponseEntity.ok(product);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        Product createdProduct = productService.saveProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    @PutMapping("/{key}")
    public ResponseEntity<Product> updateProduct(@PathVariable String key, @RequestBody Product product) {
        Product existingProduct = productService.getProductByKey(key);
        if (existingProduct != null) {
            product.setId(existingProduct.getId()); // Ensure ID is maintained for update
            Product updatedProduct = productService.saveProduct(product);
            return ResponseEntity.ok(updatedProduct);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String key) {
        productService.deleteProductByKey(key);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/price-range")
    public ResponseEntity<Map<String, BigDecimal>> getPriceRange() {
        return ResponseEntity.ok(productService.getPriceRange());
    }
}