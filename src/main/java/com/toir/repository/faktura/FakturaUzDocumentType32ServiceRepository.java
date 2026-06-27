package com.toir.repository.faktura;

import com.toir.entity.faktura.FakturaUzDocumentType32Service;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FakturaUzDocumentType32ServiceRepository extends JpaRepository<FakturaUzDocumentType32Service, UUID> {
    List<FakturaUzDocumentType32Service> findAllByDocumentUniqueIdAndIsDeletedFalseOrderByNumberAsc(String documentUniqueId);

    @Modifying
    @Query("delete from FakturaUzDocumentType32Service s where s.documentUniqueId = :documentUniqueId")
    void deleteByDocumentUniqueId(@Param("documentUniqueId") String documentUniqueId);
}
