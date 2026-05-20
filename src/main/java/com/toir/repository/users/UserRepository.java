package com.toir.repository.users;

import com.toir.entity.users.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @Query(value = "SELECT * FROM users WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<User> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM users WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<User> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM users WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<User> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM users WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();


    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.primaryRole LEFT JOIN FETCH u.department WHERE u.username = :username AND u.isDeleted = false")
    Optional<User> findByUsernameAndIsDeletedFalse(@Param("username") String username);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.primaryRole LEFT JOIN FETCH u.department WHERE u.isDeleted = false")
    List<User> findAllWithRolesAndIsDeletedFalse();

    @Query(value = """
            SELECT u.id
            FROM User u
            WHERE u.isDeleted = false
              AND (:search IS NULL OR :search = ''
                   OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(u.phone, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY u.updatedAt DESC
            """)
    Page<UUID> searchIds(@Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.primaryRole LEFT JOIN FETCH u.department WHERE u.id IN :ids AND u.isDeleted = false")
    List<User> findAllWithRolesByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.primaryRole LEFT JOIN FETCH u.department WHERE u.email = :email AND u.isDeleted = false")
    Optional<User> findByEmailAndIsDeletedFalse(@Param("email") String email);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE username = :username AND is_deleted = false)", nativeQuery = true)
    boolean existsByUsernameAndIsDeletedFalse(@Param("username") String username);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE email = :email AND is_deleted = false)", nativeQuery = true)
    boolean existsByEmailAndIsDeletedFalse(@Param("email") String email);
}
