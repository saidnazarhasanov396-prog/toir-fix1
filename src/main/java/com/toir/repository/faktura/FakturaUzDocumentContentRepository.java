package com.toir.repository.faktura;

import com.toir.entity.faktura.FakturaUzDocumentContent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FakturaUzDocumentContentRepository extends JpaRepository<FakturaUzDocumentContent, UUID> {
    Optional<FakturaUzDocumentContent> findByDocumentUniqueIdAndIsDeletedFalse(String documentUniqueId);
}
