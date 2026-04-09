package net.tecgurus.repository;

import net.tecgurus.model.ImportHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio para el historial de importaciones de Excel
 */
@Repository
public interface ImportHistoryRepository extends MongoRepository<ImportHistory, String> {
    
    /**
     * Busca todos los historiales ordenados por fecha de importación descendente
     */
    List<ImportHistory> findAllByOrderByImportDateDesc();
    
    /**
     * Busca historiales por usuario ordenados por fecha descendente
     */
    List<ImportHistory> findByUsernameOrderByImportDateDesc(String username);
    
    /**
     * Busca historiales por nombre de archivo
     */
    List<ImportHistory> findByFileNameContainingIgnoreCase(String fileName);
    
    /**
     * Busca historiales por estado
     */
    List<ImportHistory> findByStatusOrderByImportDateDesc(String status);
    
    /**
     * Busca historiales en un rango de fechas
     */
    List<ImportHistory> findByImportDateBetweenOrderByImportDateDesc(
        LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Busca historial por importLogId
     */
    Optional<ImportHistory> findByImportLogId(String importLogId);
    
    /**
     * Busca historiales por nombre de archivo y fecha aproximada
     * Útil para relacionar con el historial local del frontend
     */
    @Query("{ 'fileName': ?0, 'importDate': { $gte: ?1, $lte: ?2 } }")
    List<ImportHistory> findByFileNameAndDateRange(String fileName, 
        LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Cuenta el número total de importaciones por usuario
     */
    long countByUsername(String username);
    
    /**
     * Cuenta el número de importaciones exitosas
     */
    long countByStatus(String status);
    
    /**
     * Busca los últimos N historiales
     */
    List<ImportHistory> findTop20ByOrderByImportDateDesc();
}
