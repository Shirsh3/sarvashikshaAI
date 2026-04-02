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

    /**
     * Set username + password for the active account of the given role (Teacher, Admin, or Super Admin).
     * Does not delete data — only updates credentials (or replaces the user row if the username changes).
     */
    @Transactional
    public AppUserEntity resetCredentials(UserRole role, String newUsername, String newPassword) {
        if (role != UserRole.TEACHER && role != UserRole.ADMIN && role != UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("invalid_role");
        }
        String u = newUsername == null ? "" : newUsername.trim();
        if (u.isBlank()) {
            throw new IllegalArgumentException("username_required");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("password_required");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("password_too_short");
        }

        AppUserEntity account = appUserRepository.findActiveAnyByRole(role)
                .orElseThrow(() -> new IllegalStateException("no_active_user_for_role"));

        if (!account.getUsername().equals(u) && appUserRepository.findByUsername(u).isPresent()) {
            throw new IllegalArgumentException("username_already_exists");
        }

        String hash = passwordEncoder.encode(newPassword);

        if (account.getUsername().equals(u)) {
            account.setPasswordHash(hash);
            return appUserRepository.save(account);
        }

        AppUserEntity replacement = new AppUserEntity();
        replacement.setUsername(u);
        replacement.setPasswordHash(hash);
        replacement.setRole(role);
        replacement.setActive(true);
        replacement.setCreatedAt(account.getCreatedAt());

        appUserRepository.deleteById(account.getUsername());
        return appUserRepository.save(replacement);
    }
}
