package net.tecgurus.controller;

import net.tecgurus.model.ImportLog;
import net.tecgurus.model.ImportLogEntry;
import net.tecgurus.model.ImportHistory;
import net.tecgurus.service.ImportLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/import-logs")
public class ImportLogController {
    private static final Logger logger = LoggerFactory.getLogger(ImportLogController.class);

    @Autowired
    private ImportLogService importLogService;

    /**
     * Obtiene todos los logs de importación ordenados por fecha descendente
     */
    @GetMapping
    public ResponseEntity<List<ImportLog>> getAllImportLogs() {
        try {
            List<ImportLog> logs = importLogService.getAllImportLogs();
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error al obtener logs de importación: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtiene un log de importación específico por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ImportLog> getImportLogById(@PathVariable String id) {
        try {
            ImportLog log = importLogService.getImportLogById(id);
            if (log != null) {
                return ResponseEntity.ok(log);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error al obtener log de importación {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtiene el detalle completo de una importación (log + entradas)
     */
    @GetMapping("/{id}/details")
    public ResponseEntity<Map<String, Object>> getImportLogDetails(@PathVariable String id) {
        try {
            ImportLog log = importLogService.getImportLogById(id);
            if (log == null) {
                return ResponseEntity.notFound().build();
            }

            List<ImportLogEntry> entries = importLogService.getImportLogEntries(id);

            Map<String, Object> response = new HashMap<>();
            response.put("log", log);
            response.put("entries", entries);

            // Agregar estadísticas adicionales
            Map<String, Integer> operationCounts = new HashMap<>();
            operationCounts.put("INSERT", 0);
            operationCounts.put("UPDATE", 0);
            operationCounts.put("DELETE", 0);
            operationCounts.put("ERROR", 0);

            for (ImportLogEntry entry : entries) {
                String operation = entry.getOperation();
                operationCounts.put(operation, operationCounts.getOrDefault(operation, 0) + 1);
            }

            response.put("operationCounts", operationCounts);
            response.put("totalEntries", entries.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error al obtener detalles del log de importación {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtiene logs de importación por usuario
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ImportLog>> getImportLogsByUser(@PathVariable String userId) {
        try {
            List<ImportLog> logs = importLogService.getImportLogsByUser(userId);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            logger.error("Error al obtener logs de importación para usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtiene solo las entradas de un log específico (para paginación futura)
     */
    @GetMapping("/{id}/entries")
    public ResponseEntity<List<ImportLogEntry>> getImportLogEntries(@PathVariable String id) {
        try {
            List<ImportLogEntry> entries = importLogService.getImportLogEntries(id);
            return ResponseEntity.ok(entries);
        } catch (Exception e) {
            logger.error("Error al obtener entradas del log de importación {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Obtiene todo el historial de importaciones desde MongoDB
     */
    @GetMapping("/history")
    public ResponseEntity<List<ImportHistory>> getImportHistory() {
        try {
            List<ImportHistory> history = importLogService.getAllImportHistory();
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("Error al obtener historial de importaciones: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Obtiene historial de importaciones por usuario
     */
    @GetMapping("/history/user/{username}")
    public ResponseEntity<List<ImportHistory>> getImportHistoryByUser(@PathVariable String username) {
        try {
            List<ImportHistory> history = importLogService.getImportHistoryByUser(username);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("Error al obtener historial de importaciones para usuario {}: {}", username, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Obtiene los últimos 20 historiales de importación
     */
    @GetMapping("/history/recent")
    public ResponseEntity<List<ImportHistory>> getRecentImportHistory() {
        try {
            List<ImportHistory> history = importLogService.getRecentImportHistory();
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("Error al obtener historial reciente de importaciones: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Busca historial por nombre de archivo y rango de fechas
     */
    @GetMapping("/history/search")
    public ResponseEntity<List<ImportHistory>> searchImportHistory(
            @RequestParam String fileName,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            java.time.LocalDateTime start = java.time.LocalDateTime.parse(startDate);
            java.time.LocalDateTime end = java.time.LocalDateTime.parse(endDate);
            
            List<ImportHistory> history = importLogService.findHistoryByFileNameAndDateRange(fileName, start, end);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("Error al buscar historial de importaciones: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
