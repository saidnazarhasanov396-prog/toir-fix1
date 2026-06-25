package com.toir.repository.users;

import com.toir.entity.users.EmployeePicture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeePictureRepository extends JpaRepository<EmployeePicture, UUID> {

    @Query("""
            select ep
            from EmployeePicture ep
            join fetch ep.file f
            join ep.employee employee
            where employee.id = :employeeId
              and employee.isDeleted = false
              and ep.deleted = false
              and f.deleted = false
            order by ep.uploadedAt desc
            """)
    List<EmployeePicture> findAllByEmployeeId(@Param("employeeId") UUID employeeId);

    @Query("""
            select ep
            from EmployeePicture ep
            join fetch ep.file f
            join ep.employee employee
            where ep.id = :id
              and employee.isDeleted = false
              and ep.deleted = false
              and f.deleted = false
            """)
    Optional<EmployeePicture> findByIdActive(@Param("id") UUID id);
}
