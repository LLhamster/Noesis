package com.example.httpreading.modules.evolution;

import static com.example.httpreading.prompts.PromptTemplates.param;

import com.example.httpreading.prompts.PromptTemplates;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.httpreading.service.ModelClient;
import com.example.httpreading.service.ModelClientException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * Independent evidence-only judge. It receives no baseline/candidate label and
 * interprets concrete details according to the declared evidence-use mode.
 */
@Service
public class EvidenceBoundaryJudge {
    private static final int MAX_PARSE_ATTEMPTS = 2;
    private static final Set<String> CLASSIFICATIONS = Set.of(
        "SUPPORTED",
        "SUPPORTED_PARAPHRASE",
        "GENERAL_KNOWLEDGE",
        "EPISTEMIC_BOUNDARY_STATEMENT",
        "PEDAGOGICAL_ILLUSTRATION",
        "LABELED_POSSIBLE_SCENARIO",
        "DISCLOSED_UNGROUNDED_CONTENT",
        "UNGROUNDED_DETAIL",
        "FALSE_SOURCE_ATTRIBUTION",
        "UNSUPPORTED_FACTUAL_CLAIM");
    private static final Set<String> LABEL_POSITIONS = Set.of(
        "BEFORE_OR_AT_FIRST", "AFTER", "MISSING", "NOT_APPLICABLE");
    private static final Pattern DIRECT_UNSUPPORTED_SOURCE_ATTRIBUTION = Pattern.compile(
        "(?s)(?:根据《[^》]+》|《[^》]+》中(?:记载|指出|描述)|"
            + "(?:原文|书中|报告中)(?:记载|指出|描述))");
    private static final Pattern BOOK_AS_KEY_SOURCE = Pattern.compile(
        "(?m)关键来源[^\\r\\n]{0,300}");
    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;

    public EvidenceBoundaryJudge(ModelClient modelClient, ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
    }

    public EvidenceReview review(EvolutionEvalCase evalCase, String answer) {
        String lastError = "";
        for (int attempt = 1; attempt <= MAX_PARSE_ATTEMPTS; attempt++) {
            try {
                return parse(
                    modelClient.chat(
                        prompt(evalCase, answer), ModelClient.ChatOptions.deterministic()),
                    evalCase);
            } catch (ModelClientException exception) {
                return deterministicFallback(
                    evalCase,
                    answer,
                    "evidence judge model failed: " + exception.getMessage());
            } catch (Exception exception) {
                lastError = exception.getMessage();
            }
        }
        return deterministicFallback(
            evalCase,
            answer,
            "evidence judge parse failed: " + lastError);
    }

    private EvidenceReview deterministicFallback(EvolutionEvalCase evalCase,
                                                 String answer,
                                                 String modelError) {
        List<EvidenceIssue> issues = deterministicIssues(evalCase, answer);
        if (!issues.isEmpty()) {
            return new EvidenceReview(
                true,
                issues,
                List.of(),
                modelError + "; deterministic fallback detected an explicit violation");
        }
        return EvidenceReview.error(
            modelError + "; deterministic fallback was inconclusive");
    }

    private List<EvidenceIssue> deterministicIssues(EvolutionEvalCase evalCase, String answer) {
        if (hasBookEvidence(evalCase) || answer == null || answer.isBlank()) {
            return List.of();
        }
        Matcher directMatcher = DIRECT_UNSUPPORTED_SOURCE_ATTRIBUTION.matcher(answer);
        if (directMatcher.find()) {
            return List.of(issue(
                EvidenceIssueType.FALSE_SOURCE_ATTRIBUTION,
                1,
                List.of(directMatcher.group()),
                "当前用例没有书籍证据，回答却把具体书籍、原文或报告列为事实来源。"));
        }
        Matcher keySourceMatcher = BOOK_AS_KEY_SOURCE.matcher(answer);
        while (keySourceMatcher.find()) {
            String matched = keySourceMatcher.group();
            if (isExplicitlyUnverifiedMemoryReference(matched)) {
                continue;
            }
            return List.of(issue(
                EvidenceIssueType.FALSE_SOURCE_ATTRIBUTION,
                1,
                List.of(matched),
                "当前用例没有书籍证据，回答却把具体书籍列为关键事实来源。"));
        }
        return List.of();
    }

    private boolean hasBookEvidence(EvolutionEvalCase evalCase) {
        return evalCase.collectedEvidence().stream().anyMatch(value ->
            "current_page".equals(value.type()) || value.type().startsWith("rag"));
    }

    private String prompt(EvolutionEvalCase evalCase, String answer) throws Exception {
        String inputJson = objectMapper.writeValueAsString(java.util.Map.of(
            "question", evalCase.request().getQuestion(),
            "collectedEvidence", evalCase.collectedEvidence(),
            "evidencePolicy", evalCase.expectedBehavior().evidencePolicy(),
            "answer", answer == null ? "" : answer));
        return PromptTemplates.render("evolution/evidence-boundary", param("inputJson", inputJson));
    }

    private EvidenceReview parse(String raw,
                                 EvolutionEvalCase evalCase) throws Exception {
        EvolutionEvalCase.EvidencePolicy policy =
            evalCase.expectedBehavior().evidencePolicy();
        JsonNode root = parseObject(raw);
        List<ClaimReview> claims = parseClaims(root);
        if (!root.has("possible_scenario_present") || !root.has("scenario_label_position")) {
            throw new IllegalArgumentException("evidence judge must report possible scenario status");
        }
        String labelPosition = root.path("scenario_label_position").asText("NOT_APPLICABLE");
        if (!LABEL_POSITIONS.contains(labelPosition)) {
            throw new IllegalArgumentException("unknown scenario label position: " + labelPosition);
        }

        boolean disclosurePresent = "BEFORE_OR_AT_FIRST".equals(labelPosition)
            || "AFTER".equals(labelPosition);
        claims = reviewDisputedClaimsIfNeeded(
            evalCase, claims, disclosurePresent);
        List<EvidenceIssue> issues = new ArrayList<>();
        List<ClaimReview> falseAttributions = claimsOf(
            claims, "FALSE_SOURCE_ATTRIBUTION");
        if (!falseAttributions.isEmpty()) {
            issues.add(issue(
                EvidenceIssueType.FALSE_SOURCE_ATTRIBUTION,
                falseAttributions.size(),
                claimExamples(falseAttributions),
                "回答把证据外内容归因成原文、真实事件或实际来源。"));
        }

        List<ClaimReview> ungrounded = claims.stream()
            .filter(claim -> Set.of(
                "UNGROUNDED_DETAIL",
                "UNSUPPORTED_FACTUAL_CLAIM",
                "DISCLOSED_UNGROUNDED_CONTENT",
                "LABELED_POSSIBLE_SCENARIO").contains(claim.classification()))
            .toList();
        List<ClaimReview> generalKnowledge = claimsOf(claims, "GENERAL_KNOWLEDGE");
        List<ClaimReview> pedagogical = claimsOf(claims, "PEDAGOGICAL_ILLUSTRATION");

        switch (policy.evidenceUseMode()) {
            case STRICT_SOURCE -> {
                List<ClaimReview> unsupported = new ArrayList<>(ungrounded);
                unsupported.addAll(generalKnowledge);
                unsupported.addAll(pedagogical);
                if (!unsupported.isEmpty()) {
                    issues.add(issue(
                        EvidenceIssueType.STRICT_SOURCE_UNSUPPORTED_CONTENT,
                        unsupported.size(),
                        claimExamples(unsupported),
                        "严格来源模式包含证据外内容，免责声明不能替代证据。"));
                }
            }
            case SOURCE_GROUNDED_NARRATIVE -> {
                List<ClaimReview> constructed = new ArrayList<>(ungrounded);
                constructed.addAll(pedagogical);
                boolean scenarioPresent = root.path("possible_scenario_present").asBoolean(false)
                    || !constructed.isEmpty();
                if (scenarioPresent && !disclosurePresent) {
                    issues.add(issue(
                        EvidenceIssueType.UNLABELED_UNGROUNDED_SECTION,
                        Math.max(1, constructed.size()),
                        claimExamples(constructed),
                        "历史或事实叙事中的证据外场景没有说明其为可能、想象或助手自主构造内容。"));
                }
                if (!policy.allowGeneralExplanation() && !generalKnowledge.isEmpty()) {
                    issues.add(issue(
                        EvidenceIssueType.UNSUPPORTED_FACTUAL_CLAIM,
                        generalKnowledge.size(),
                        claimExamples(generalKnowledge),
                        "当前证据策略不允许一般知识补充。"));
                }
            }
            case PEDAGOGICAL_ILLUSTRATION -> {
                List<ClaimReview> factual = claims.stream()
                    .filter(claim -> "UNSUPPORTED_FACTUAL_CLAIM".equals(claim.classification())
                        || "UNGROUNDED_DETAIL".equals(claim.classification()))
                    .toList();
                if (!factual.isEmpty()) {
                    issues.add(issue(
                        EvidenceIssueType.UNSUPPORTED_FACTUAL_CLAIM,
                        factual.size(),
                        claimExamples(factual),
                        "回答包含不属于教学举例、却以真实事实口吻陈述的无依据内容。"));
                }
                if (!policy.allowGeneralExplanation() && !generalKnowledge.isEmpty()) {
                    issues.add(issue(
                        EvidenceIssueType.UNSUPPORTED_FACTUAL_CLAIM,
                        generalKnowledge.size(),
                        claimExamples(generalKnowledge),
                        "当前证据策略不允许一般知识补充。"));
                }
            }
        }

        boolean constructedContentRemains = claims.stream().anyMatch(claim ->
            Set.of(
                "UNGROUNDED_DETAIL",
                "UNSUPPORTED_FACTUAL_CLAIM",
                "DISCLOSED_UNGROUNDED_CONTENT",
                "LABELED_POSSIBLE_SCENARIO",
                "PEDAGOGICAL_ILLUSTRATION").contains(claim.classification()));
        addStrategyViolations(
            root, policy, disclosurePresent, constructedContentRemains, issues);
        return new EvidenceReview(true, mergeIssues(List.of(), issues), List.copyOf(claims), "");
    }

    private List<ClaimReview> reviewDisputedClaimsIfNeeded(
        EvolutionEvalCase evalCase,
        List<ClaimReview> claims,
        boolean disclosurePresent) throws Exception {
        List<IndexedClaim> disputed = new ArrayList<>();
        for (int index = 0; index < claims.size(); index++) {
            ClaimReview claim = claims.get(index);
            if ("UNSUPPORTED_FACTUAL_CLAIM".equals(claim.classification())
                || "UNGROUNDED_DETAIL".equals(claim.classification())) {
                disputed.add(new IndexedClaim(index, claim));
            }
        }
        if (disputed.isEmpty()
            || evalCase.expectedBehavior().evidencePolicy().evidenceUseMode()
                == EvidenceUseMode.SOURCE_GROUNDED_NARRATIVE
                && disclosurePresent) {
            return claims;
        }

        JsonNode root = parseObject(modelClient.chat(
            entailmentPrompt(evalCase, disputed),
            ModelClient.ChatOptions.deterministic()));
        JsonNode reviews = root.path("reviews");
        if (!reviews.isArray() || reviews.size() != disputed.size()) {
            throw new IllegalArgumentException(
                "entailment judge must review every disputed claim exactly once");
        }
        Map<Integer, EntailmentReview> byIndex = new java.util.LinkedHashMap<>();
        for (JsonNode node : reviews) {
            int index = node.path("index").asInt(-1);
            String relation = node.path("relation").asText("");
            if (!Set.of(
                "ENTAILED",
                "SUPPORTED_ELABORATION",
                "EPISTEMIC_BOUNDARY_STATEMENT",
                "PEDAGOGICAL_ILLUSTRATION",
                "UNSUPPORTED_EXTERNAL_FACT").contains(relation)) {
                throw new IllegalArgumentException(
                    "unknown entailment relation: " + relation);
            }
            boolean expected = disputed.stream().anyMatch(value -> value.index() == index);
            if (!expected || byIndex.put(
                index, new EntailmentReview(relation, node.path("reason").asText())) != null) {
                throw new IllegalArgumentException(
                    "entailment judge returned duplicate or unknown index: " + index);
            }
        }
        if (byIndex.size() != disputed.size()) {
            throw new IllegalArgumentException(
                "entailment judge omitted a disputed claim");
        }

        List<ClaimReview> reviewed = new ArrayList<>(claims);
        for (IndexedClaim indexed : disputed) {
            EntailmentReview entailment = byIndex.get(indexed.index());
            String classification = switch (entailment.relation()) {
                case "ENTAILED" -> "SUPPORTED_PARAPHRASE";
                case "SUPPORTED_ELABORATION" -> "GENERAL_KNOWLEDGE";
                case "EPISTEMIC_BOUNDARY_STATEMENT" -> "EPISTEMIC_BOUNDARY_STATEMENT";
                case "PEDAGOGICAL_ILLUSTRATION" -> "PEDAGOGICAL_ILLUSTRATION";
                default -> "UNSUPPORTED_FACTUAL_CLAIM";
            };
            reviewed.set(indexed.index(), new ClaimReview(
                indexed.claim().claim(),
                classification,
                "语义复核：" + entailment.reason()));
        }
        return List.copyOf(reviewed);
    }

    private String entailmentPrompt(EvolutionEvalCase evalCase,
                                    List<IndexedClaim> disputed) throws Exception {
        List<Map<String, Object>> claims = disputed.stream().map(value -> Map.<String, Object>of(
            "index", value.index(),
            "claim", value.claim().claim(),
            "initialReason", value.claim().reason())).toList();
        String inputJson = objectMapper.writeValueAsString(Map.of(
            "question", evalCase.request().getQuestion(),
            "collectedEvidence", evalCase.collectedEvidence(),
            "evidencePolicy", evalCase.expectedBehavior().evidencePolicy(),
            "claims", claims));
        return PromptTemplates.render("evolution/evidence-claims", param("inputJson", inputJson));
    }

    private List<ClaimReview> parseClaims(JsonNode root) {
        List<ClaimReview> claims = new ArrayList<>();
        root.path("claims").forEach(node -> {
            String classification = node.path("classification").asText();
            if (!CLASSIFICATIONS.contains(classification)) {
                throw new IllegalArgumentException(
                    "unknown evidence classification: " + classification);
            }
            String claim = node.path("claim").asText("").trim();
            if (claim.isBlank()) {
                throw new IllegalArgumentException("evidence claim must not be blank");
            }
            claims.add(new ClaimReview(
                claim, classification, node.path("reason").asText()));
        });
        if (claims.isEmpty()) {
            throw new IllegalArgumentException("evidence judge must classify at least one claim");
        }
        return claims;
    }

    private void addStrategyViolations(JsonNode root,
                                       EvolutionEvalCase.EvidencePolicy policy,
                                       boolean disclosurePresent,
                                       boolean constructedContentRemains,
                                       List<EvidenceIssue> issues) {
        root.path("violations").forEach(node -> {
            String value = node.asText("").trim();
            if (value.isBlank() || isSuccessMessage(value)) return;
            String normalized = value.replaceAll("\\s+", "");
            if (normalized.matches(".*(前置|场景开始|声明|标注).*")
                && policy.evidenceUseMode() == EvidenceUseMode.SOURCE_GROUNDED_NARRATIVE) {
                if (constructedContentRemains
                    && !disclosurePresent
                    && issues.stream().noneMatch(issue ->
                        issue.type() == EvidenceIssueType.UNLABELED_UNGROUNDED_SECTION)) {
                    issues.add(issue(
                        EvidenceIssueType.UNLABELED_UNGROUNDED_SECTION,
                        1, List.of(), value));
                }
                return;
            }
            if (normalized.matches(".*(错误归因|事实来源|原文事实|真实来源).*")
                && issues.stream().noneMatch(issue ->
                    issue.type() == EvidenceIssueType.FALSE_SOURCE_ATTRIBUTION)) {
                issues.add(issue(
                    EvidenceIssueType.FALSE_SOURCE_ATTRIBUTION,
                    1, List.of(), value));
            }
        });
    }

    private EvidenceIssue issue(EvidenceIssueType type,
                                int count,
                                List<String> examples,
                                String detail) {
        return new EvidenceIssue(
            type,
            summary(type, detail),
            count,
            examples,
            correction(type));
    }

    private String summary(EvidenceIssueType type, String detail) {
        return switch (type) {
            case FALSE_SOURCE_ATTRIBUTION ->
                "回答把当前证据无法确认的内容归因成原文、书籍记录、真实事件或实际来源。";
            case UNLABELED_UNGROUNDED_SECTION ->
                "历史或事实叙事包含证据外补写，但回答没有说明其为自主构造或无资料依据。";
            case STRICT_SOURCE_UNSUPPORTED_CONTENT ->
                "严格来源模式下出现了证据没有直接支持的补充内容。";
            case UNSUPPORTED_FACTUAL_CLAIM ->
                "回答包含不属于安全教学举例、却以事实口吻陈述的无依据内容。";
        };
    }

    private String correction(EvidenceIssueType type) {
        return switch (type) {
            case FALSE_SOURCE_ATTRIBUTION ->
                "删除“原文记载、书中事实、真实来源”等归因；只有取得对应证据后才能保留。";
            case UNLABELED_UNGROUNDED_SECTION ->
                "在回答中加入“以下为助手自主构造、没有资料依据，仅用于理解”或等价声明；"
                    + "一次声明即可覆盖整个连续场景。";
            case STRICT_SOURCE_UNSUPPORTED_CONTENT ->
                "删除所有证据外内容；若证据不足，直接说明缺少哪些资料，不能用免责声明绕过。";
            case UNSUPPORTED_FACTUAL_CLAIM ->
                "若任务允许补写，将内容放入明确披露的自主构造段落；否则删除该内容或补充可靠证据。";
        };
    }

    private List<EvidenceIssue> mergeIssues(List<EvidenceIssue> first,
                                            List<EvidenceIssue> second) {
        Map<EvidenceIssueType, List<EvidenceIssue>> grouped =
            new EnumMap<>(EvidenceIssueType.class);
        java.util.stream.Stream.concat(first.stream(), second.stream())
            .forEach(issue -> grouped.computeIfAbsent(issue.type(), ignored -> new ArrayList<>())
                .add(issue));
        List<EvidenceIssue> merged = new ArrayList<>();
        grouped.forEach((type, values) -> {
            int count = values.stream().mapToInt(EvidenceIssue::count).max().orElse(1);
            List<String> examples = values.stream().flatMap(value -> value.examples().stream())
                .distinct().limit(2).toList();
            String summary = values.stream().map(EvidenceIssue::summary)
                .filter(value -> !value.isBlank()).findFirst().orElse("");
            String correction = values.stream().map(EvidenceIssue::correction)
                .filter(value -> !value.isBlank()).findFirst().orElse(correction(type));
            merged.add(new EvidenceIssue(type, summary, count, examples, correction));
        });
        return List.copyOf(merged);
    }

    private List<ClaimReview> claimsOf(List<ClaimReview> claims, String classification) {
        return claims.stream()
            .filter(claim -> classification.equals(claim.classification()))
            .toList();
    }

    private boolean isExplicitlyUnverifiedMemoryReference(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", "");
        boolean memoryReference = normalized.contains("历史记忆摘要")
            || normalized.contains("历史记忆")
            || normalized.contains("之前记录")
            || normalized.contains("memory");
        boolean unverified = normalized.contains("未核验")
            || normalized.contains("无法核验")
            || normalized.matches(".*未提供.{0,12}原文.*");
        return memoryReference
            && unverified
            && !DIRECT_UNSUPPORTED_SOURCE_ATTRIBUTION.matcher(normalized).find();
    }

    private List<String> claimExamples(List<ClaimReview> claims) {
        return claims.stream().map(ClaimReview::claim).distinct().limit(2).toList();
    }

    private boolean isSuccessMessage(String value) {
        String normalized = value.replaceAll("\\s+", "");
        return normalized.matches(
            "^(未发现违规|没有发现违规|无违规|符合证据策略|通过证据检查)([：:。.!！].*)?$");
    }

    private JsonNode parseObject(String raw) throws Exception {
        String value = raw == null ? "" : raw.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("evidence judge did not return JSON");
        }
        return objectMapper.readTree(value.substring(start, end + 1));
    }

    public record ClaimReview(String claim, String classification, String reason) {
        public ClaimReview {
            claim = claim == null ? "" : claim.trim();
            classification = classification == null ? "" : classification.trim();
            reason = reason == null ? "" : reason.trim();
        }
    }

    private record IndexedClaim(int index, ClaimReview claim) {
    }

    private record EntailmentReview(String relation, String reason) {
    }

    public record EvidenceReview(boolean evaluated,
                                 List<EvidenceIssue> issues,
                                 List<ClaimReview> claims,
                                 String error) {
        public EvidenceReview {
            issues = issues == null ? List.of() : List.copyOf(issues);
            claims = claims == null ? List.of() : List.copyOf(claims);
            error = error == null ? "" : error.trim();
        }

        public static EvidenceReview error(String error) {
            return new EvidenceReview(false, List.of(), List.of(), error);
        }

        public List<String> violations() {
            return issues.stream().map(issue -> {
                String examples = issue.examples().isEmpty()
                    ? ""
                    : "；示例：" + String.join("；", issue.examples());
                return issue.summary() + examples + "；如何修改：" + issue.correction();
            }).toList();
        }

        public boolean safe() {
            return evaluated && issues.isEmpty();
        }
    }
}
