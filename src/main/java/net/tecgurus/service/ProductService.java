package net.tecgurus.service;

import net.tecgurus.controller.response.ImportResult;
import net.tecgurus.controller.response.ProductResponse;
import net.tecgurus.model.Product;
import net.tecgurus.model.Quote;
import net.tecgurus.model.ImportLog;
import net.tecgurus.controller.request.QuoteItem;
import net.tecgurus.repository.ProductRepository;
import net.tecgurus.repository.QuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Map;

import java.util.*;

@Service
public class ProductService {
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    @Autowired private ProductRepository productRepository;

    @Autowired private MongoTemplate mongoTemplate;

    @Autowired private QuoteRepository quoteRepository;

    @Autowired private ImportLogService importLogService;

    public Product getProductByKey(String key) {
        return productRepository.findByKey(key);
    }

    @Transactional
    public Product saveProduct(Product product) {
        if (product.getId() == null) {
            product.setCreatedAt(LocalDateTime.now());
        }
        return productRepository.save(product);
    }

    public void deleteProductByKey(String key) {
        productRepository.deleteByKey(key);
    }

    @Transactional
    public Page<ProductResponse> findCatalog(
            int page,
            int size,
            String key,
            String name,
            String productLine,
            BigDecimal priceMin,
            BigDecimal priceMax,
            String sort
    ) {
        logger.info("=== INICIO DE BÚSQUEDA DE CATÁLOGO ===");
        logger.info("Parámetros recibidos - page: {}, size: {}, key: {}, name: {}, productLine: {}, priceMin: {}, priceMax: {}, sort: {}",
                page, size, key, name, productLine, priceMin, priceMax, sort);

        try {
            // Ordenación
            logger.debug("Construyendo criterios de ordenación...");
            Sort springSort = buildSort(sort);
            logger.debug("Ordenación configurada: {}", springSort);

            // Construir consulta
            logger.debug("Construyendo consulta...");
            Query query = buildQuery(key, name, productLine, priceMin, priceMax);
            logger.debug("Consulta construida: {}", query.getQueryObject().toJson());

            // Contar total de resultados
            logger.debug("Contando resultados totales...");
            long total = mongoTemplate.count(query, Product.class);
            logger.info("Total de productos que coinciden con los filtros: {}", total);

            if (total == 0) {
                logger.warn("No se encontraron productos con los filtros aplicados");
            }

            // Aplicar paginación
            logger.debug("Aplicando paginación - página: {}, tamaño: {}", page, size);
            Pageable pageable = PageRequest.of(page, size, springSort);
            query.with(pageable);

            // Obtener resultados
            logger.debug("Ejecutando consulta...");
            List<Product> products = mongoTemplate.find(query, Product.class);
            logger.info("Se encontraron {} productos en la página actual", products.size());

            if (!products.isEmpty()) {
                logger.debug("Primer producto encontrado - Clave: {}, Nombre: {}, Precio: {}",
                        products.get(0).getKey(),
                        products.get(0).getName(),
                        products.get(0).getPrice());
            }

            // Mapear a DTOs
            logger.debug("Mapeando resultados a DTOs...");
            List<ProductResponse> responses = products.stream()
                    .map(this::mapToProductResponse)
                    .collect(Collectors.toList());

            logger.info("=== FIN DE BÚSQUEDA DE CATÁLOGO ===");
            return new PageImpl<>(responses, pageable, total);

        } catch (Exception e) {
            logger.error("Error al buscar en el catálogo: {}", e.getMessage(), e);
            throw new RuntimeException("Error al buscar en el catálogo: " + e.getMessage(), e);
        }
    }

    private Query buildQuery(String key, String name, String productLine,
                             BigDecimal priceMin, BigDecimal priceMax) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        // Clave (ahora con búsqueda de prefijo)
        if (StringUtils.hasText(key)) {
            String safeKey = Pattern.quote(key.trim());
            criteriaList.add(Criteria.where("key").regex("^" + safeKey, "i"));
        }

        if (StringUtils.hasText(name)) {
            criteriaList.add(Criteria.where("name").regex(name, "i"));
        }
        if (StringUtils.hasText(productLine)) {
            criteriaList.add(Criteria.where("productLine").regex(productLine, "i"));
        }

        // Filtro de precios mejorado
        if (priceMin != null || priceMax != null) {
            Criteria priceCriteria = new Criteria();

            if (priceMin != null && priceMax != null) {
                logger.info("Aplicando filtro de precio entre {} y {}", priceMin, priceMax);
                priceCriteria.and("price").gte(priceMin.doubleValue()).lte(priceMax.doubleValue());
            } else if (priceMin != null) {
                logger.info("Aplicando filtro de precio mínimo: {}", priceMin);
                priceCriteria.and("price").gte(priceMin.doubleValue());
            } else if (priceMax != null) {
                logger.info("Aplicando filtro de precio máximo: {}", priceMax);
                priceCriteria.and("price").lte(priceMax.doubleValue());
            }

            criteriaList.add(priceCriteria);
        }

        // Combinar todos los criterios
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        logger.info("Consulta generada: {}", query.getQueryObject().toJson());
        return query;
    }

    private Sort buildSort(String sort) {
        if (StringUtils.hasText(sort)) {
            String[] parts = sort.split(",");
            String field = parts[0];
            String dir = (parts.length == 2) ? parts[1] : "asc";

            Set<String> allowedFields = Set.of("name", "key", "productLine", "price", "lastSaleDate", "createdAt");
            if (!allowedFields.contains(field)) {
                field = "createdAt";
            }

            Sort main = Sort.by(
                    "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC,
                    field
            );

            // Solo agrega el tie-breaker por 'key' si el campo principal NO es 'key'
            if (!"key".equals(field)) {
                main = main.and(Sort.by("key").ascending());
            }

            return main;
        }

        // Default estable
        return Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by("key").ascending());
    }


    private ProductResponse mapToProductResponse(Product product) {
        return new ProductResponse(
                product.getKey(),
                product.getName(),
                product.getProductLine(),
                product.getPrice()
        );
    }
    private static final int BATCH_SIZE = 1000; // Tamaño de lote optimizado para MongoDB

    /**
     * Método optimizado para importación masiva de productos usando batch processing
     * Procesa productos en lotes para mejorar el rendimiento y reducir el uso de memoria
     */
    @Transactional
    public ImportResult saveAllOptimized(List<Product> products, String fileName, String userId, String username) {
        if (products == null || products.isEmpty()) {
            logger.info("Lista de productos vacía, no hay nada que procesar.");
            return new ImportResult(null, Collections.emptyList(), 0);
        }

        logger.info("Iniciando importación optimizada (rápida y segura) de {} productos.", products.size());
        long startTime = System.currentTimeMillis();

        ImportLog importLog = importLogService.createImportLog(fileName, userId, username);
        String importLogId = importLog.getId();

        int errorCount = 0;
        List<String> errorMessages = new ArrayList<>();

        try {
            // 1. Eliminar duplicados en la lista de Excel (mantener el último)
            Map<String, Product> uniqueProductsMap = products.stream()
                    .filter(p -> p != null && StringUtils.hasText(p.getKey()))
                    .collect(Collectors.toMap(Product::getKey, p -> p, (existing, replacement) -> replacement));
            List<Product> uniqueProducts = new ArrayList<>(uniqueProductsMap.values());
            logger.info("Productos únicos en el Excel: {}", uniqueProducts.size());

            // 2. Eliminar productos de la BD que ya no están en el Excel
            Set<String> excelProductKeys = uniqueProductsMap.keySet();
            long deletedCount = deleteProductsNotInExcel(excelProductKeys, importLogId);

            // 3. Buscar todos los productos existentes en la BD en una sola consulta
            Map<String, Product> existingProductsDb = productRepository.findByKeyIn(new ArrayList<>(excelProductKeys))
                    .stream()
                    .collect(Collectors.toMap(Product::getKey, p -> p));
            logger.info("Encontrados {} productos existentes en la base de datos.", existingProductsDb.size());

            // 4. Preparar operaciones en lote (BulkOperations)
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Product.class);
            int insertedCount = 0;
            int updatedCount = 0;
            LocalDateTime now = LocalDateTime.now();

            for (Product productFromExcel : uniqueProducts) {
                Product existingProduct = existingProductsDb.get(productFromExcel.getKey());

                if (existingProduct == null) {
                    // Es un producto NUEVO
                    productFromExcel.setCreatedAt(now);
                    bulkOps.insert(productFromExcel);
                    importLogService.logProductInsert(importLogId, productFromExcel);
                    insertedCount++;
                } else {
                    // Es un producto EXISTENTE, se actualiza
                    boolean hasChanges = !Objects.equals(existingProduct.getName(), productFromExcel.getName()) ||
                            !Objects.equals(existingProduct.getProductLine(), productFromExcel.getProductLine()) ||
                            (productFromExcel.getPrice().compareTo(existingProduct.getPrice()) != 0);

                    if (hasChanges) {
                        Update update = new Update()
                                .set("name", productFromExcel.getName())
                                .set("productLine", productFromExcel.getProductLine())
                                .set("price", productFromExcel.getPrice()) // Ya es BigDecimal gracias al Controller
                                .set("lastSaleDate", productFromExcel.getLastSaleDate());

                        Query query = new Query(Criteria.where("_id").is(existingProduct.getId()));
                        bulkOps.updateOne(query, update);
                        importLogService.logProductUpdate(importLogId, existingProduct, productFromExcel);
                        updatedCount++;
                    }
                }
            }

            // 5. Ejecutar todas las operaciones en lote
            if (insertedCount > 0 || updatedCount > 0) {
                bulkOps.execute();
                logger.info("Operaciones en lote ejecutadas: {} inserciones, {} actualizaciones.", insertedCount, updatedCount);
            } else {
                logger.info("No se realizaron inserciones ni actualizaciones en lote.");
            }

            long endTime = System.currentTimeMillis();
            long processingTimeMs = endTime - startTime;

            importLogService.finalizeImportLog(importLogId, products.size(), insertedCount, updatedCount, (int) deletedCount, errorCount, processingTimeMs, errorMessages);

            logger.info("Importación optimizada terminada. Insertados: {}, Actualizados: {}, Eliminados: {}. Tiempo: {} ms",
                    insertedCount, updatedCount, deletedCount, processingTimeMs);

            // La lógica de actualizar cotizaciones se puede añadir aquí si se necesita
            return new ImportResult(importLogId, uniqueProducts, 0);

        } catch (Exception e) {
            logger.error("Error durante la importación optimizada: {}", e.getMessage(), e);
            importLogService.finalizeImportLog(importLogId, products.size(), 0, 0, 0, errorCount + 1, 0, List.of(e.getMessage()));
            throw new RuntimeException("Error al guardar los productos de forma optimizada: " + e.getMessage(), e);
        }
    }

    /**
     * Elimina productos que no están presentes en el Excel de forma optimizada
     */
    private long deleteProductsNotInExcel(Set<String> excelProductKeys, String importLogId) {
        try {
            logger.info("Iniciando eliminación de productos no presentes en Excel...");

            // Buscar productos existentes que NO están en el Excel
            Query query = new Query();
            if (!excelProductKeys.isEmpty()) {
                query.addCriteria(Criteria.where("key").nin(excelProductKeys));
            }

            // Obtener productos completos a eliminar para logging
            List<Product> productsToDelete = mongoTemplate.find(query, Product.class);

            if (productsToDelete.isEmpty()) {
                logger.info("No hay productos para eliminar");
                return 0;
            }

            logger.info("Productos a eliminar: {}", productsToDelete.size());

            // Registrar eliminaciones en el log de importación
            for (Product product : productsToDelete) {
                importLogService.logProductDelete(importLogId, product);
            }

            // Eliminar en una sola operación
            long deletedCount = mongoTemplate.remove(query, Product.class).getDeletedCount();

            logger.info("Eliminación completada. Productos eliminados: {}", deletedCount);
            return deletedCount;

        } catch (Exception e) {
            logger.error("Error al eliminar productos no presentes en Excel: {}", e.getMessage(), e);
            throw new RuntimeException("Error al eliminar productos: " + e.getMessage(), e);
        }
    }

    /**
     * Procesa un lote individual de productos con rastreo de cambios completos
     */
    private Map<String, Product> processBatchOptimizedWithProductTracking(List<Product> batch, String importLogId) {
        try {
            // 1. Obtener claves del lote
            List<String> keys = batch.stream()
                    .map(Product::getKey)
                    .collect(Collectors.toList());

            // 2. Buscar productos existentes en una sola consulta
            Map<String, Product> existingProducts = productRepository.findByKeyIn(keys)
                    .stream()
                    .collect(Collectors.toMap(
                            Product::getKey,
                            p -> p,
                            (existing, replacement) -> replacement
                    ));

            // 3. Separar productos nuevos y existentes, rastrear cambios completos
            List<Product> newProducts = new ArrayList<>();
            List<Product> updatedProducts = new ArrayList<>();
            Map<String, Product> productChanges = new HashMap<>();
            LocalDateTime now = LocalDateTime.now();

            for (Product product : batch) {
                Product existing = existingProducts.get(product.getKey());

                if (existing != null) {
                    // Actualizar producto existente
                    product.setId(existing.getId());
                    product.setCreatedAt(existing.getCreatedAt()); // Mantener fecha de creación original

                    // Verificar si algún campo del producto cambió
                    boolean hasChanges = false;

                    // Verificar cambios en precio
                    if (existing.getPrice() != null && product.getPrice() != null &&
                            existing.getPrice().compareTo(product.getPrice()) != 0) {
                        hasChanges = true;
                        logger.debug("Cambio de precio detectado para {}: {} -> {}",
                                product.getKey(), existing.getPrice(), product.getPrice());
                    }

                    // Verificar cambios en nombre
                    if (!Objects.equals(existing.getName(), product.getName())) {
                        hasChanges = true;
                        logger.debug("Cambio de nombre detectado para {}: {} -> {}",
                                product.getKey(), existing.getName(), product.getName());
                    }

                    // Verificar cambios en línea de producto
                    if (!Objects.equals(existing.getProductLine(), product.getProductLine())) {
                        hasChanges = true;
                        logger.debug("Cambio de línea de producto detectado para {}: {} -> {}",
                                product.getKey(), existing.getProductLine(), product.getProductLine());
                    }

                    // Si hay cambios, guardar el producto completo actualizado
                    if (hasChanges) {
                        productChanges.put(product.getKey(), product);
                        // Registrar actualización en el log de importación
                        importLogService.logProductUpdate(importLogId, existing, product);
                    }

                    updatedProducts.add(product);
                } else {
                    // Nuevo producto
                    product.setCreatedAt(now);
                    newProducts.add(product);
                    // Registrar inserción en el log de importación
                    importLogService.logProductInsert(importLogId, product);
                }
            }

            // 4. Usar BulkOperations para operaciones batch optimizadas
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Product.class);

            // Insertar productos nuevos
            if (!newProducts.isEmpty()) {
                bulkOps.insert(newProducts);
            }

            // Actualizar productos existentes
            for (Product product : updatedProducts) {
                Query query = new Query(Criteria.where("_id").is(product.getId()));
                Update update = new Update()
                        .set("name", product.getName())
                        .set("productLine", product.getProductLine())
                        .set("price", product.getPrice())
                        .set("lastSaleDate", product.getLastSaleDate());
                bulkOps.updateOne(query, update);
            }

            // Ejecutar todas las operaciones en lote
            if (!newProducts.isEmpty() || !updatedProducts.isEmpty()) {
                bulkOps.execute();
            }

            // 5. Retornar cambios de productos detectados
            return productChanges;

        } catch (Exception e) {
            logger.error("Error al procesar lote optimizado: {}", e.getMessage(), e);
            throw new RuntimeException("Error al procesar lote de productos: " + e.getMessage(), e);
        }
    }

    /**
     * Actualiza cotizaciones vigentes con información completa de productos actualizados
     * Solo actualiza cotizaciones que:
     * 1. No tienen pedido aún (status != "CONFIRMADO")
     * 2. Están vigentes (expiredAt >= hoy, sin considerar hora)
     * Actualiza: precio, nombre, y toda la información del producto
     */
    private long updateValidQuotesWithUpdatedProducts(Map<String, Product> productUpdates) {
        try {
            if (productUpdates.isEmpty()) {
                return 0;
            }

            logger.info("Iniciando actualización de cotizaciones vigentes para {} productos con cambios completos", productUpdates.size());

            // Calcular fecha de hoy sin hora para comparación
            LocalDate today = LocalDate.now();
            Date todayStart = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());

            // Buscar cotizaciones que necesitan actualización
            Query quotesQuery = new Query();
            quotesQuery.addCriteria(
                    Criteria.where("status").ne("CONFIRMADO")  // No confirmadas (sin pedido)
                            .and("expiredAt").gte(todayStart)          // Vigentes (no expiradas)
            );

            List<Quote> validQuotes = mongoTemplate.find(quotesQuery, Quote.class);
            logger.info("Cotizaciones vigentes encontradas: {}", validQuotes.size());

            if (validQuotes.isEmpty()) {
                return 0;
            }

            long updatedQuotesCount = 0;
            BulkOperations quoteBulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Quote.class);

            for (Quote quote : validQuotes) {
                boolean quoteNeedsUpdate = false;
                BigDecimal newTotalPrice = BigDecimal.ZERO;

                // Verificar cada item de la cotización
                if (quote.getItems() != null) {
                    for (QuoteItem item : quote.getItems()) {
                        Product updatedProduct = productUpdates.get(item.getKey());

                        if (updatedProduct != null) {
                            // Actualizar TODA la información del producto en la cotización
                            item.setUnitPrice(updatedProduct.getPrice());
                            item.setName(updatedProduct.getName());
                            // La clave (key) no cambia, es el identificador único

                            quoteNeedsUpdate = true;
                            logger.debug("Actualizando información completa en cotización {}, producto {}: precio={}, nombre={}",
                                    quote.getNumber(), item.getKey(), updatedProduct.getPrice(), updatedProduct.getName());
                        }

                        // Calcular nuevo total (siempre recalcular para asegurar consistencia)
                        BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                        newTotalPrice = newTotalPrice.add(itemTotal);
                    }
                }

                // Si la cotización necesita actualización, añadirla al bulk
                if (quoteNeedsUpdate) {
                    // Aplicar margen de ganancia si existe
                    if (quote.getProfitMargin() != null && quote.getProfitMargin().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal marginMultiplier = BigDecimal.ONE.add(quote.getProfitMargin().divide(BigDecimal.valueOf(100)));
                        newTotalPrice = newTotalPrice.multiply(marginMultiplier);
                    }

                    Query updateQuery = new Query(Criteria.where("_id").is(quote.getId()));
                    Update update = new Update()
                            .set("items", quote.getItems())
                            .set("totalPrice", newTotalPrice);

                    quoteBulkOps.updateOne(updateQuery, update);
                    updatedQuotesCount++;
                }
            }

            // Ejecutar actualizaciones en lote
            if (updatedQuotesCount > 0) {
                quoteBulkOps.execute();
                logger.info("Cotizaciones actualizadas con información completa de productos: {}", updatedQuotesCount);
            }

            return updatedQuotesCount;

        } catch (Exception e) {
            logger.error("Error al actualizar cotizaciones vigentes: {}", e.getMessage(), e);
            throw new RuntimeException("Error al actualizar cotizaciones con información completa de productos: " + e.getMessage(), e);
        }
    }

    @Transactional
    public List<Product> saveAll(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            // 1. Filtrar productos nulos y sin clave válida
            List<Product> validProducts = products.stream()
                    .filter(Objects::nonNull)
                    .filter(p -> p.getKey() != null && !p.getKey().trim().isEmpty())
                    .collect(Collectors.toList());

            // 2. Eliminar duplicados en el lote actual (mantener el último)
            Map<String, Product> uniqueProducts = validProducts.stream()
                    .collect(Collectors.toMap(
                            Product::getKey,
                            p -> p,
                            (existing, replacement) -> replacement
                    ));

            // 3. Obtener claves únicas
            List<String> keys = new ArrayList<>(uniqueProducts.keySet());

            // 4. Buscar productos existentes
            Map<String, Product> existingProducts = productRepository.findByKeyIn(keys)
                    .stream()
                    .collect(Collectors.toMap(
                            Product::getKey,
                            p -> p,
                            (existing, replacement) -> replacement
                    ));

            // 5. Preparar lista final para guardar
            List<Product> productsToSave = new ArrayList<>();

            for (Map.Entry<String, Product> entry : uniqueProducts.entrySet()) {
                Product product = entry.getValue();
                Product existing = existingProducts.get(entry.getKey());

                if (existing != null) {
                    // Actualizar el ID para que sea una actualización
                    product.setId(existing.getId());
                }
                productsToSave.add(product);
            }

            // 6. Guardar todo en lote
            return productRepository.saveAll(productsToSave);
        } catch (Exception e) {
            logger.error("Error al guardar productos: {}", e.getMessage(), e);
            throw new RuntimeException("Error al guardar los productos: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getPriceRange() {
        try {
            List<Product> products = productRepository.findAllPrices();
            if (products == null || products.isEmpty()) {
                throw new RuntimeException("No se encontraron productos en la base de datos");
            }

            List<BigDecimal> validPrices = products.stream()
                    .map(Product::getPrice)
                    .filter(Objects::nonNull)
                    .filter(price -> price.compareTo(BigDecimal.ZERO) >= 0)
                    .collect(Collectors.toList());

            if (validPrices.isEmpty()) {
                throw new RuntimeException("No se encontraron precios válidos en la base de datos");
            }

            BigDecimal min = validPrices.stream()
                    .min(BigDecimal::compareTo)
                    .orElseThrow(() -> new RuntimeException("Error al calcular el precio mínimo"));

            BigDecimal max = validPrices.stream()
                    .max(BigDecimal::compareTo)
                    .orElseThrow(() -> new RuntimeException("Error al calcular el precio máximo"));

            return Map.of("min", min, "max", max);
        } catch (Exception e) {
            throw new RuntimeException("Error al obtener el rango de precios: " + e.getMessage(), e);
        }
    }
}