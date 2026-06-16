package com.toir.repository.users;

import com.toir.entity.users.EmployeeWorkRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeWorkRoleRepository extends JpaRepository<EmployeeWorkRole, UUID> {

    Optional<EmployeeWorkRole> findByCodeAndIsDeletedFalse(String code);

    List<EmployeeWorkRole> findAllByCodeInAndIsDeletedFalse(Collection<String> codes);

    List<EmployeeWorkRole> findAllByActiveTrueAndIsDeletedFalseOrderByCodeAsc();
}
