package net.tecgurus.repository;

import net.tecgurus.model.ImportLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ImportLogRepository extends MongoRepository<ImportLog, String> {
    
    @Query(value = "{}", sort = "{ 'importDate' : -1 }")
    List<ImportLog> findAllOrderByImportDateDesc();
    
    @Query(value = "{ 'userId' : ?0 }", sort = "{ 'importDate' : -1 }")
    List<ImportLog> findByUserIdOrderByImportDateDesc(String userId);
    
    @Query(value = "{ 'importDate' : { $gte : ?0, $lte : ?1 } }", sort = "{ 'importDate' : -1 }")
    List<ImportLog> findByImportDateBetweenOrderByImportDateDesc(Instant startDate, Instant endDate);
}
