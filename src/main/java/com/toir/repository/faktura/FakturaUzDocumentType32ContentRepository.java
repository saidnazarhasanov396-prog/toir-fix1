package com.toir.repository.faktura;

import com.toir.entity.faktura.FakturaUzDocumentType32Content;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FakturaUzDocumentType32ContentRepository extends JpaRepository<FakturaUzDocumentType32Content, UUID> {
    Optional<FakturaUzDocumentType32Content> findByDocumentUniqueIdAndIsDeletedFalse(String documentUniqueId);
}
