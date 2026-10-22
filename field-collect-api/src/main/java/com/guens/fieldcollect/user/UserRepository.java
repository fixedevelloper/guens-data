package com.guens.fieldcollect.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByDeviceId(String deviceId);

    /** Liaison atomique : ne réussit que si aucun appareil n'est encore lié. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u set u.deviceId = :deviceId, u.deviceModel = :model, u.deviceBoundAt = :boundAt
            where u.id = :id and u.deviceId is null
            """)
    int bindDevice(@Param("id") Long id, @Param("deviceId") String deviceId, @Param("model") String model,
                   @Param("boundAt") java.time.Instant boundAt);
}
