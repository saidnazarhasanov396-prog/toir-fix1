package com.toir.repository;

import com.toir.entity.UserFcmToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, UUID> {

    Optional<UserFcmToken> findByTokenAndIsDeletedFalse(String token);

    List<UserFcmToken> findAllByUserIdAndActiveTrueAndIsDeletedFalseOrderByLastSeenAtDesc(UUID userId);

    List<UserFcmToken> findAllByUserIdAndIsDeletedFalseOrderByLastSeenAtDesc(UUID userId);

    @Query("""
            select t
            from UserFcmToken t
            where t.userId = :userId
              and t.token = :token
              and t.isDeleted = false
            """)
    Optional<UserFcmToken> findByUserIdAndTokenAndIsDeletedFalse(@Param("userId") UUID userId,
                                                                 @Param("token") String token);
}
