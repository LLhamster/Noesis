package com.example.httpreading.modules.profile.repository;

import java.util.List;
import java.util.Optional;

import com.example.httpreading.modules.profile.domain.UserKnowledgeState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserKnowledgeStateRepository extends JpaRepository<UserKnowledgeState, Long> {
    Optional<UserKnowledgeState> findByUserIdAndDomainAndTopic(String userId, String domain, String topic);

    List<UserKnowledgeState> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<UserKnowledgeState> findByUserIdAndDomainOrderByUpdatedAtDesc(String userId, String domain);

    List<UserKnowledgeState> findByRelatedBookId(Long relatedBookId);
}
