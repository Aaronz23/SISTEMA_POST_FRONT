package net.tecgurus.service;

import net.tecgurus.model.ImportLog;
import net.tecgurus.model.ImportLogEntry;
import net.tecgurus.model.ImportHistory;
import net.tecgurus.model.Product;
import net.tecgurus.repository.ImportLogRepository;
import net.tecgurus.repository.ImportLogEntryRepository;
import net.tecgurus.repository.ImportHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class ImportLogService {

    @Autowired
    private ImportLogRepository importLogRepository;

    @Autowired
    private ImportLogEntryRepository importLogEntryRepository;
    
    @Autowired
    private ImportHistoryRepository importHistoryRepository;

    /**
     * Crea un nuevo log de importación
     */
    public ImportLog createImportLog(String fileName, String userId, String username) {
        ImportLog importLog = new ImportLog(fileName, userId, username);
        return importLogRepository.save(importLog);
    }

    /**
     * Finaliza un log de importación con estadísticas
     */
    public ImportLog finalizeImportLog(String importLogId, int totalRows, int insertedCount, 
                                     int updatedCount, int deletedCount, int errorCount, 
                                     long processingTimeMs, List<String> errorMessages) {
        ImportLog importLog = importLogRepository.findById(importLogId).orElse(null);
        if (importLog != null) {
            importLog.setTotalRows(totalRows);
            importLog.setInsertedCount(insertedCount);
            importLog.setUpdatedCount(updatedCount);
            importLog.setDeletedCount(deletedCount);
            importLog.setErrorCount(errorCount);
            importLog.setProcessingTimeMs(processingTimeMs);
            importLog.setErrorMessages(errorMessages);
            
            // Determinar status
            if (errorCount == 0) {
                importLog.setStatus("SUCCESS");
            } else if (insertedCount + updatedCount > 0) {
                importLog.setStatus("PARTIAL");
            } else {
                importLog.setStatus("FAILED");
            }
            
            ImportLog savedLog = importLogRepository.save(importLog);
            
            // Crear automáticamente el historial en MongoDB
            createImportHistory(savedLog);
            
            return savedLog;
        }
        return null;
    }

    /**
     * Registra una inserción de producto de manera asíncrona
     */
    public void logProductInsert(String importLogId, Product product) {
        CompletableFuture.runAsync(() -> {
            ImportLogEntry entry = new ImportLogEntry(importLogId, product.getKey(), "INSERT");
            entry.setProductId(product.getId());
            
            Map<String, Object> newValues = new HashMap<>();
            newValues.put("name", product.getName());
            newValues.put("productLine", product.getProductLine());
            newValues.put("unitPrice", product.getPrice());
            newValues.put("lastSaleDate", product.getLastSaleDate());
            entry.setNewValues(newValues);
            
            importLogEntryRepository.save(entry);
        });
    }

    /**
     * Registra una actualización de producto de manera asíncrona
     */
    public void logProductUpdate(String importLogId, Product oldProduct, Product newProduct) {
        CompletableFuture.runAsync(() -> {
            ImportLogEntry entry = new ImportLogEntry(importLogId, newProduct.getKey(), "UPDATE");
            entry.setProductId(newProduct.getId());
            
            Map<String, Object> previousValues = new HashMap<>();
            Map<String, Object> newValues = new HashMap<>();
            
            // Comparar y registrar solo los campos que cambiaron
            if (!Objects.equals(oldProduct.getName(), newProduct.getName())) {
                previousValues.put("name", oldProduct.getName());
                newValues.put("name", newProduct.getName());
            }
            
            if (!Objects.equals(oldProduct.getProductLine(), newProduct.getProductLine())) {
                previousValues.put("productLine", oldProduct.getProductLine());
                newValues.put("productLine", newProduct.getProductLine());
            }
            
            if (!Objects.equals(oldProduct.getPrice(), newProduct.getPrice())) {
                previousValues.put("unitPrice", oldProduct.getPrice());
                newValues.put("unitPrice", newProduct.getPrice());
            }
            
            if (!Objects.equals(oldProduct.getLastSaleDate(), newProduct.getLastSaleDate())) {
                previousValues.put("lastSaleDate", oldProduct.getLastSaleDate());
                newValues.put("lastSaleDate", newProduct.getLastSaleDate());
            }
            
            entry.setPreviousValues(previousValues);
            entry.setNewValues(newValues);
            
            importLogEntryRepository.save(entry);
        });
    }

    /**
     * Registra una eliminación de producto de manera asíncrona
     */
    public void logProductDelete(String importLogId, Product product) {
        CompletableFuture.runAsync(() -> {
            ImportLogEntry entry = new ImportLogEntry(importLogId, product.getKey(), "DELETE");
            entry.setProductId(product.getId());
            
            Map<String, Object> previousValues = new HashMap<>();
            previousValues.put("name", product.getName());
            previousValues.put("productLine", product.getProductLine());
            previousValues.put("unitPrice", product.getPrice());
            previousValues.put("lastSaleDate", product.getLastSaleDate());
            entry.setPreviousValues(previousValues);
            
            importLogEntryRepository.save(entry);
        });
    }

    /**
     * Registra un error de procesamiento de manera asíncrona
     */
    public void logProcessingError(String importLogId, String productKey, String errorMessage) {
        CompletableFuture.runAsync(() -> {
            ImportLogEntry entry = new ImportLogEntry(importLogId, productKey, "ERROR");
            entry.setErrorMessage(errorMessage);
            importLogEntryRepository.save(entry);
        });
    }

    /**
     * Obtiene todos los logs de importación
     */
    public List<ImportLog> getAllImportLogs() {
        return importLogRepository.findAllOrderByImportDateDesc();
    }

    /**
     * Obtiene un log de importación por ID
     */
    public ImportLog getImportLogById(String id) {
        return importLogRepository.findById(id).orElse(null);
    }

    /**
     * Obtiene todas las entradas de un log de importación específico
     */
    public List<ImportLogEntry> getImportLogEntries(String importLogId) {
        return importLogEntryRepository.findByImportLogIdOrderByTimestampAsc(importLogId);
    }

    /**
     * Obtiene logs de importación por usuario
     */
    public List<ImportLog> getImportLogsByUser(String userId) {
        return importLogRepository.findByUserIdOrderByImportDateDesc(userId);
    }
    
    /**
     * Crea un historial de importación en MongoDB basado en un ImportLog
     */
    private void createImportHistory(ImportLog importLog) {
        CompletableFuture.runAsync(() -> {
            try {
                ImportHistory history = new ImportHistory(importLog);
                importHistoryRepository.save(history);
            } catch (Exception e) {
                System.err.println("Error creating import history: " + e.getMessage());
            }
        });
    }
    
    /**
     * Obtiene todo el historial de importaciones ordenado por fecha descendente
     */
    public List<ImportHistory> getAllImportHistory() {
        return importHistoryRepository.findAllByOrderByImportDateDesc();
    }
    
    /**
     * Obtiene historial de importaciones por usuario
     */
    public List<ImportHistory> getImportHistoryByUser(String username) {
        return importHistoryRepository.findByUsernameOrderByImportDateDesc(username);
    }
    
    /**
     * Obtiene los últimos 20 historiales de importación
     */
    public List<ImportHistory> getRecentImportHistory() {
        return importHistoryRepository.findTop20ByOrderByImportDateDesc();
    }
    
    /**
     * Busca historial por nombre de archivo y rango de fechas
     * Útil para relacionar con el historial local del frontend
     */
    public List<ImportHistory> findHistoryByFileNameAndDateRange(String fileName, 
            java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        return importHistoryRepository.findByFileNameAndDateRange(fileName, startDate, endDate);
    }
    
    /**
     * Obtiene historial por importLogId
     */
    public ImportHistory getHistoryByImportLogId(String importLogId) {
        return importHistoryRepository.findByImportLogId(importLogId).orElse(null);
    }
}
