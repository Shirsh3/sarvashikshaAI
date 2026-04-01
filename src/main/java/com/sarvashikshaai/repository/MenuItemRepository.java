package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.MenuItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, Long> {

    Optional<MenuItemEntity> findByKey(String key);

    List<MenuItemEntity> findAllByEnabledTeacherTrueOrderBySortOrderAsc();

    List<MenuItemEntity> findAllByEnabledAdminTrueOrderBySortOrderAsc();

    List<MenuItemEntity> findAllByEnabledTeacherTrueOrEnabledAdminTrueOrderBySortOrderAsc();
}

