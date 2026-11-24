package com.example.httpreading.modules.cognition.service;

import com.example.httpreading.modules.cognition.domain.KnowledgeConcept;

record MatchedConcept(KnowledgeConcept concept, double score, String reason, boolean aliasMatch) {
}
