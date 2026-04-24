package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"primaryRole", "roles"})
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = {"primaryRole", "roles"})
    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
