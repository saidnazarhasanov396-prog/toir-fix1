package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ServiceClass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface ServiceClassRepository extends JpaRepository<ServiceClass, UUID> {
    java.util.Optional<ServiceClass> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ServiceClass> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<ServiceClass> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM service_classes WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
