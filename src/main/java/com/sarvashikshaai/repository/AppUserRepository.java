package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.UserRole;
import com.sarvashikshaai.model.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUserEntity, String> {

    Optional<AppUserEntity> findByUsername(String username);

    @Query("SELECT u FROM AppUserEntity u WHERE u.active = true AND u.role = :role")
    Optional<AppUserEntity> findActiveAnyByRole(@Param("role") UserRole role);
}

