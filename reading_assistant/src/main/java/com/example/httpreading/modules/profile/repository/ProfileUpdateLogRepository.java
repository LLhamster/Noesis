package com.example.httpreading.modules.profile.repository;

import com.example.httpreading.modules.profile.domain.ProfileUpdateLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileUpdateLogRepository extends JpaRepository<ProfileUpdateLog, Long> {
}
