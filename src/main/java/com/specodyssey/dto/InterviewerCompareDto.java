package com.specodyssey.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 면접관의 "공유받은 이력" 목록과 "지원자 비교" 화면에 보여줄 읽기 전용 뷰.
 * 관련 요구사항: FR-82 · 83 · 84
 * 지원자 칸에는 지원자가 공유 링크에서 공개한 범위의 값만 들어간다.
 */
public class InterviewerCompareDto {

    private List<Criterion> criteria = new ArrayList<>();
    private int totalWeight;
    private List<Applicant> applicants = new ArrayList<>();

    /** 회사 요구 역량 한 줄 (FR-83). */
    public static class Criterion {
        private final Long id;
        private final String skillName;
        private final int weight;

        public Criterion(Long id, String skillName, int weight) {
            this.id = id;
            this.skillName = skillName;
            this.weight = weight;
        }

        public Long getId() {
            return id;
        }

        public String getSkillName() {
            return skillName;
        }

        public int getWeight() {
            return weight;
        }
    }

    /** 담아 둔 지원자 한 명. */
    public static class Applicant {
        private Long itemId;
        private String label;       // 지원자 이름. 기본 이력을 공개하지 않았거나 이름이 없으면 담은 순서("지원자 1")
        private String addedDate;   // yyyy-MM-dd
        private LocalDateTime addedAt; // "최근 담은 순" 정렬용
        private String token;       // 이력 보기 링크용. 지금 열 수 없는 링크면 null
        private ShareViewDto view;  // 공유가 중단·만료됐으면 null
        private String certText;    // "정보처리기사 외 1개" / "없음"
        private String certFullText; // 표 칸에 마우스를 올렸을 때 보여줄 전체 목록 "정보처리기사, SQLD"
        private List<Boolean> matches = new ArrayList<>(); // criteria와 같은 순서. 기술 스택 비공개면 비어 있다
        private Integer fitScore;   // 적합도 점수. 계산할 수 없으면 null
        private String growthText;  // 성장 잠재력 요약. 비공개면 null

        public boolean isAvailable() {
            return view != null;
        }

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public LocalDateTime getAddedAt() {
            return addedAt;
        }

        public void setAddedAt(LocalDateTime addedAt) {
            this.addedAt = addedAt;
        }

        public String getAddedDate() {
            return addedDate;
        }

        public void setAddedDate(String addedDate) {
            this.addedDate = addedDate;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public ShareViewDto getView() {
            return view;
        }

        public void setView(ShareViewDto view) {
            this.view = view;
        }

        public String getCertText() {
            return certText;
        }

        public void setCertText(String certText) {
            this.certText = certText;
        }

        public String getCertFullText() {
            return certFullText;
        }

        public void setCertFullText(String certFullText) {
            this.certFullText = certFullText;
        }

        public List<Boolean> getMatches() {
            return matches;
        }

        public void setMatches(List<Boolean> matches) {
            this.matches = matches;
        }

        public Integer getFitScore() {
            return fitScore;
        }

        public void setFitScore(Integer fitScore) {
            this.fitScore = fitScore;
        }

        public String getGrowthText() {
            return growthText;
        }

        public void setGrowthText(String growthText) {
            this.growthText = growthText;
        }
    }

    public List<Criterion> getCriteria() {
        return criteria;
    }

    public void setCriteria(List<Criterion> criteria) {
        this.criteria = criteria;
    }

    public int getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(int totalWeight) {
        this.totalWeight = totalWeight;
    }

    public List<Applicant> getApplicants() {
        return applicants;
    }

    public void setApplicants(List<Applicant> applicants) {
        this.applicants = applicants;
    }
}
