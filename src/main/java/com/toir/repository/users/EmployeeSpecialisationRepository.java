package com.toir.repository.users;

import com.toir.entity.users.EmployeeSpecialisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeSpecialisationRepository extends JpaRepository<EmployeeSpecialisation, UUID> {

    Optional<EmployeeSpecialisation> findByIdAndIsDeletedFalse(UUID id);

    List<EmployeeSpecialisation> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    List<EmployeeSpecialisation> findAllByIdInAndIsDeletedFalse(Collection<UUID> ids);
}
