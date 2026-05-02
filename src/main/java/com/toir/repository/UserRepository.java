package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    java.util.Optional<User> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<User> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<User> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();


    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.primaryRole WHERE u.username = :username AND u.isDeleted = false")
    Optional<User> findByUsernameAndIsDeletedFalse(@Param("username") String username);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.primaryRole WHERE u.isDeleted = false")
    List<User> findAllWithRolesAndIsDeletedFalse();

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.primaryRole WHERE u.email = :email AND u.isDeleted = false")
    Optional<User> findByEmailAndIsDeletedFalse(@Param("email") String email);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE username = :username AND is_deleted = false)", nativeQuery = true)
    boolean existsByUsernameAndIsDeletedFalse(@Param("username") String username);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE email = :email AND is_deleted = false)", nativeQuery = true)
    boolean existsByEmailAndIsDeletedFalse(@Param("email") String email);
}
