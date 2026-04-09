package net.tecgurus.controller;

import net.tecgurus.controller.response.ImportResult;
import net.tecgurus.model.Product;
import net.tecgurus.repository.ProductRepository;
import net.tecgurus.service.ProductService;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/excel")
public class ExcelController {

    @Autowired
    private ProductRepository productRepository;

    private static final Logger logger = LoggerFactory.getLogger(ExcelController.class);

    @Autowired
    private ProductService productService;

    /**
     * Recibe:
     * - file:               Excel (.xls/.xlsx)
     * - uploadedBy:         (opcional) usuario que sube el archivo
     * - ignoreWarnings:     (opcional) bandera del front
     * - foundIssues:        (opcional) cantidad de issues detectadas en el front
     * - issuesReport:       (opcional) JSON con el detalle de issues (como archivo)
     */
    @PostMapping(
            value = "/import-products",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> importProductsFromExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "ignoreWarnings", required = false, defaultValue = "true") boolean ignoreWarnings,
            @RequestParam(value = "foundIssues", required = false, defaultValue = "0") int foundIssues
    ) {
        try {
            logger.info("=== INICIANDO IMPORTACIÓN OPTIMIZADA ===");
            logger.info("Archivo: {}, Usuario: {}, IgnoreWarnings: {}, FoundIssues: {}",
                    file.getOriginalFilename(), username, ignoreWarnings, foundIssues);

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("Por favor selecciona un archivo");
            }

            String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            String who = (username == null || username.isBlank()) ? "desconocido" : username.trim();

            long startTime = System.currentTimeMillis();

            try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                Sheet sheet = workbook.getSheetAt(0);
                Iterator<Row> rowIterator = sheet.iterator();

                if (!rowIterator.hasNext()) {
                    return ResponseEntity.badRequest().body("El archivo está vacío");
                }

                // Saltar encabezados
                rowIterator.next();

                List<Product> products = new ArrayList<>();
                int rowNum = 1;
                int processedRows = 0;
                int errorRows = 0;

                logger.info("Iniciando lectura y mapeo de filas del Excel...");

                while (rowIterator.hasNext()) {
                    rowNum++;
                    try {
                        Row row = rowIterator.next();
                        Product product = mapRowToProduct(row);
                        if (product != null) {
                            products.add(product);
                            processedRows++;

                            // Log de progreso cada 1000 productos
                            if (processedRows % 1000 == 0) {
                                logger.info("Procesadas {} filas del Excel...", processedRows);
                            }
                        }
                    } catch (Exception e) {
                        errorRows++;
                        logger.error("Error en la fila {}: {}", rowNum, e.getMessage());

                        // Si hay demasiados errores, detener el proceso
                        if (errorRows > 100) {
                            return ResponseEntity.badRequest().body(
                                    String.format("Demasiados errores en el archivo. Último error en fila %d: %s",
                                            rowNum, e.getMessage())
                            );
                        }
                    }
                }

                long readTime = System.currentTimeMillis();
                logger.info("Lectura del Excel completada. Filas procesadas: {}, Errores: {}, Tiempo: {} ms",
                        processedRows, errorRows, (readTime - startTime));

                // Llamar al servicio para guardar los productos
                ImportResult result = productService.saveAllOptimized(products, originalName, userId, who);

                // 2. Forzar la conversión de todos los precios en la BD
                long convertedCount = productRepository.convertAllPricesToDecimal();
                logger.info("Se forzó la conversión de precios a decimal para {} productos.", convertedCount);

                long endTime = System.currentTimeMillis();
                logger.info("Proceso de importación del controller finalizado en {} ms", (endTime - startTime));

                // Crear una respuesta que incluya el ID del log
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Importación completada");
                response.put("importLogId", result.getImportLogId());
                response.put("savedProductsCount", result.getSavedProducts().size());
                response.put("updatedQuotesCount", result.getUpdatedQuotesCount());
                response.put("forceConvertedCount", convertedCount); // Añadimos el conteo de la conversión forzada

                return ResponseEntity.ok(response);

            } catch (Exception e) {
                logger.error("Error al procesar el archivo Excel: {}", e.getMessage(), e);
                return ResponseEntity.status(500).body("Error interno al procesar el archivo: " + e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error en el endpoint de importación: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error en el servidor: " + e.getMessage());
        }
    }

    // ---------- Helpers de mapeo ----------
    private Product mapRowToProduct(Row row) {
        Product product = new Product();

        // Ajusta los índices a tu layout real
        product.setKey(getStringValue(row.getCell(0)));       // CVE_ART
        product.setName(getStringValue(row.getCell(1)));      // DESCR
        product.setProductLine(getStringValue(row.getCell(2)));// LIN_PROD

        // Fecha (col 3)
        Cell dateCell = row.getCell(3);
        if (dateCell != null) {
            if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                product.setLastSaleDate(dateCell.getDateCellValue());
            } else {
                String dateStr = getStringValue(dateCell);
                if (!dateStr.isEmpty()) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                        product.setLastSaleDate(sdf.parse(dateStr));
                    } catch (Exception ignored) { }
                }
            }
        }

        // Precio (col 4)
        Cell priceCell = row.getCell(4);
        if (priceCell != null) {
            try {
                double price = 0.0;
                if (priceCell.getCellType() == CellType.STRING) {
                    String priceStr = priceCell.getStringCellValue()
                            .replace("$", "")
                            .replace(",", "")
                            .trim();
                    price = Double.parseDouble(priceStr);
                } else if (priceCell.getCellType() == CellType.NUMERIC) {
                    price = priceCell.getNumericCellValue();
                }
                product.setPrice(BigDecimal.valueOf(price));
            } catch (Exception e) {
                product.setPrice(BigDecimal.ZERO);
            }
        } else {
            product.setPrice(BigDecimal.ZERO);
        }

        return product;
    }

    private String getStringValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat("dd/MM/yyyy").format(cell.getDateCellValue());
                } else {
                    double num = cell.getNumericCellValue();
                    if (num == (int) num) return String.valueOf((int) num);
                    return String.valueOf(num);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
}