package net.tecgurus.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "import_log_entries")
public class ImportLogEntry {
    @Id
    private String id;
    private String importLogId;
    private String productKey;
    private String productId;
    private String operation; // "INSERT", "UPDATE", "DELETE"
    private Instant timestamp;
    private Map<String, Object> previousValues; // Para UPDATE: valores anteriores
    private Map<String, Object> newValues; // Para INSERT/UPDATE: valores nuevos
    private String errorMessage; // Si hubo error

    // Constructors
    public ImportLogEntry() {}

    public ImportLogEntry(String importLogId, String productKey, String operation) {
        this.importLogId = importLogId;
        this.productKey = productKey;
        this.operation = operation;
        this.timestamp = Instant.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImportLogId() {
        return importLogId;
    }

    public void setImportLogId(String importLogId) {
        this.importLogId = importLogId;
    }

    public String getProductKey() {
        return productKey;
    }

    public void setProductKey(String productKey) {
        this.productKey = productKey;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getPreviousValues() {
        return previousValues;
    }

    public void setPreviousValues(Map<String, Object> previousValues) {
        this.previousValues = previousValues;
    }

    public Map<String, Object> getNewValues() {
        return newValues;
    }

    public void setNewValues(Map<String, Object> newValues) {
        this.newValues = newValues;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
