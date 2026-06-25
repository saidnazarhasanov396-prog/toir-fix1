package com.toir.repository;

import com.toir.entity.Supplier;
import com.toir.enums.SupplierType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByIdAndIsDeletedFalse(UUID id);

    List<Supplier> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    List<Supplier> findAllByIdInAndIsDeletedFalse(Collection<UUID> ids);

    boolean existsByCodeAndIsDeletedFalse(String code);

    @Query("""
            select s
            from Supplier s
            where s.isDeleted = false
              and (:active is null or s.active = :active)
              and (
                    :supplierType is null
                    or (:supplierType = com.toir.enums.SupplierType.BOTH and s.supplierType = com.toir.enums.SupplierType.BOTH)
                    or (:supplierType <> com.toir.enums.SupplierType.BOTH and (s.supplierType = :supplierType or s.supplierType = com.toir.enums.SupplierType.BOTH))
                  )
            order by s.updatedAt desc
            """)
    List<Supplier> findAllFiltered(
            @Param("active") Boolean active,
            @Param("supplierType") SupplierType supplierType
    );

    @Query("""
            select s
            from Supplier s
            where s.isDeleted = false
              and (:active is null or s.active = :active)
              and (
                    :supplierType is null
                    or (:supplierType = com.toir.enums.SupplierType.BOTH and s.supplierType = com.toir.enums.SupplierType.BOTH)
                    or (:supplierType <> com.toir.enums.SupplierType.BOTH and (s.supplierType = :supplierType or s.supplierType = com.toir.enums.SupplierType.BOTH))
                  )
              and (
                    lower(s.code) like lower(concat('%', :search, '%'))
                    or lower(s.name) like lower(concat('%', :search, '%'))
                    or lower(coalesce(s.contactPerson, '')) like lower(concat('%', :search, '%'))
                  )
            order by s.updatedAt desc
            """)
    List<Supplier> search(
            @Param("search") String search,
            @Param("active") Boolean active,
            @Param("supplierType") SupplierType supplierType
    );
}
