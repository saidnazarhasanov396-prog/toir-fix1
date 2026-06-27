package com.toir.repository.faktura;

import com.toir.entity.faktura.FakturaUzDocumentType32Part;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FakturaUzDocumentType32PartRepository extends JpaRepository<FakturaUzDocumentType32Part, UUID> {
    List<FakturaUzDocumentType32Part> findAllByDocumentUniqueIdAndIsDeletedFalseOrderByNumberAsc(String documentUniqueId);

    @Modifying
    @Query("delete from FakturaUzDocumentType32Part p where p.documentUniqueId = :documentUniqueId")
    void deleteByDocumentUniqueId(@Param("documentUniqueId") String documentUniqueId);
}
