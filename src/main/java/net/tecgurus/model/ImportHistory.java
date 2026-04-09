package net.tecgurus.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Modelo para el historial de importaciones de Excel
 * Representa un resumen de cada importación realizada
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "import_history")
public class ImportHistory {
    
    @Id
    private String id;
    
    /**
     * Referencia al log detallado de importación
     */
    private String importLogId;
    
    /**
     * Nombre del archivo importado
     */
    private String fileName;
    
    /**
     * Usuario que realizó la importación
     */
    private String username;
    
    /**
     * Fecha y hora de la importación
     */
    private LocalDateTime importDate;
    
    /**
     * Número total de filas procesadas
     */
    private Integer totalRows;
    
    /**
     * Número de errores encontrados
     */
    private Integer errorCount;
    
    /**
     * Número de warnings encontrados
     */
    private Integer warningCount;
    
    /**
     * Estado de la importación (OK, FAILED)
     */
    private String status;
    
    /**
     * Número de productos insertados
     */
    private Integer insertedCount;
    
    /**
     * Número de productos actualizados
     */
    private Integer updatedCount;
    
    /**
     * Número de productos eliminados
     */
    private Integer deletedCount;
    
    /**
     * Tiempo de procesamiento en milisegundos
     */
    private Long processingTimeMs;
    
    /**
     * Constructor para crear un historial desde un ImportLog
     */
    public ImportHistory(ImportLog importLog) {
        this.importLogId = importLog.getId();
        this.fileName = importLog.getFileName();
        this.username = importLog.getUsername();
        this.importDate = importLog.getImportDate().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        this.totalRows = importLog.getTotalRows();
        this.errorCount = importLog.getErrorCount();
        this.warningCount = 0; // Se puede calcular desde las entries si es necesario
        this.status = "COMPLETED".equals(importLog.getStatus()) ? "OK" : "FAILED";
        this.insertedCount = importLog.getInsertedCount();
        this.updatedCount = importLog.getUpdatedCount();
        this.deletedCount = importLog.getDeletedCount();
        this.processingTimeMs = importLog.getProcessingTimeMs();
    }
    
    /**
     * Constructor para crear historial con información básica
     */
    public ImportHistory(String fileName, String username, Integer totalRows, 
                        Integer errorCount, Integer warningCount, boolean success) {
        this.fileName = fileName;
        this.username = username;
        this.importDate = LocalDateTime.now();
        this.totalRows = totalRows;
        this.errorCount = errorCount;
        this.warningCount = warningCount;
        this.status = success ? "OK" : "FAILED";
        this.insertedCount = 0;
        this.updatedCount = 0;
        this.deletedCount = 0;
        this.processingTimeMs = 0L;
    }
}
