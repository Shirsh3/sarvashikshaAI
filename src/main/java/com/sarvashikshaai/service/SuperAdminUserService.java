package com.sarvashikshaai.service;

import com.sarvashikshaai.model.UserRole;
import com.sarvashikshaai.model.entity.AppUserEntity;
import com.sarvashikshaai.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuperAdminUserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AppUserEntity resetTeacherCredentials(String newUsername, String newPassword) {
        String u = newUsername == null ? "" : newUsername.trim();
        if (u.isBlank()) throw new IllegalArgumentException("username_required");
        if (newPassword == null || newPassword.isBlank()) throw new IllegalArgumentException("password_required");
        if (newPassword.length() < 6) throw new IllegalArgumentException("password_too_short");

        AppUserEntity teacher = appUserRepository.findActiveAnyByRole(UserRole.TEACHER)
                .orElseThrow(() -> new IllegalStateException("no_active_teacher_found"));

        if (!teacher.getUsername().equals(u) && appUserRepository.findByUsername(u).isPresent()) {
            throw new IllegalArgumentException("username_already_exists");
        }

        String hash = passwordEncoder.encode(newPassword);

        if (teacher.getUsername().equals(u)) {
            teacher.setPasswordHash(hash);
            return appUserRepository.save(teacher);
        }

        // Username is the PK; change by creating new row and deleting old.
        AppUserEntity replacement = new AppUserEntity();
        replacement.setUsername(u);
        replacement.setPasswordHash(hash);
        replacement.setRole(UserRole.TEACHER);
        replacement.setActive(true);
        replacement.setCreatedAt(teacher.getCreatedAt());

        appUserRepository.deleteById(teacher.getUsername());
        return appUserRepository.save(replacement);
    }
}

