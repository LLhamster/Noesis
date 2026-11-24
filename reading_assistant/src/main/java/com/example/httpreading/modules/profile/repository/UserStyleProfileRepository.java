package com.example.httpreading.modules.profile.repository;

import java.util.Optional;

import com.example.httpreading.modules.profile.domain.UserStyleProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStyleProfileRepository extends JpaRepository<UserStyleProfile, Long> {
    Optional<UserStyleProfile> findByUserId(String userId);
}
