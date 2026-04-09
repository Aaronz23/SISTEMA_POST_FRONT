package net.tecgurus.repository;

import net.tecgurus.model.ImportLogEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportLogEntryRepository extends MongoRepository<ImportLogEntry, String> {
    
    @Query(value = "{ 'importLogId' : ?0 }", sort = "{ 'timestamp' : 1 }")
    List<ImportLogEntry> findByImportLogIdOrderByTimestampAsc(String importLogId);
    
    @Query(value = "{ 'importLogId' : ?0, 'operation' : ?1 }", sort = "{ 'timestamp' : 1 }")
    List<ImportLogEntry> findByImportLogIdAndOperationOrderByTimestampAsc(String importLogId, String operation);
    
    @Query(value = "{ 'productKey' : ?0 }", sort = "{ 'timestamp' : -1 }")
    List<ImportLogEntry> findByProductKeyOrderByTimestampDesc(String productKey);
}
