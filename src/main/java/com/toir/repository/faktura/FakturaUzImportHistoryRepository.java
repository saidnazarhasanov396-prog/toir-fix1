package com.toir.repository.faktura;

import com.toir.entity.faktura.FakturaUzImportHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FakturaUzImportHistoryRepository extends JpaRepository<FakturaUzImportHistory, UUID> {
    Page<FakturaUzImportHistory> findAllByIsDeletedFalseOrderByImportedAtDesc(Pageable pageable);
    Page<FakturaUzImportHistory> findAllByEndpointIdAndIsDeletedFalseOrderByImportedAtDesc(UUID endpointId, Pageable pageable);
}
