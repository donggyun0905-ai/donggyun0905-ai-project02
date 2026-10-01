package com.specodyssey.dto;

import java.util.List;

/**
 * 데이터 인사이트 화면(/insights) 출력용 묶음. 관련 요구사항: FR-45~48
 * 카드마다 데이터가 없을 수 있으므로 각 카드 객체는 null이 아니고, 비었는지는 isEmpty 계열로 판단한다.
 * (Tomcat 10.1의 EL은 record 접근자를 못 읽어서 getter를 직접 둔다.)
 */
public class InsightViewDto {

    private String jobName;
    private PeerView peer;
    private TrendView trend;
    private List<BenchmarkTier> benchmark;
    private HeatmapView heatmap;

    /** FR-45 또래 비교. myScore/peerAverage는 데이터가 없으면 null. */
    public record PeerView(String major, String grade, Integer myScore, Integer peerAverage, int peerCount,
                           String message) {
        public String getMajor() {
            return major;
        }

        public String getGrade() {
            return grade;
        }

        public Integer getMyScore() {
            return myScore;
        }

        public Integer getPeerAverage() {
            return peerAverage;
        }

        public int getPeerCount() {
            return peerCount;
        }

        public String getMessage() {
            return message;
        }

        public boolean isComparable() {
            return myScore != null && peerAverage != null;
        }
    }

    /** FR-47 기술별 월별 언급 비율. months와 skills[].ratios의 순서가 같다. */
    public record TrendView(List<String> months, List<TrendSkill> skills) {
        public List<String> getMonths() {
            return months;
        }

        public List<TrendSkill> getSkills() {
            return skills;
        }

        public boolean isEmpty() {
            return skills.isEmpty();
        }
    }

    /** ratios: 달마다 0~100 정수, 그 달에 언급이 없으면 null. change: 직전 달 대비 증감(%p), 비교 불가면 null. */
    public record TrendSkill(String skillName, List<Integer> ratios, Integer change) {
        public String getSkillName() {
            return skillName;
        }

        public List<Integer> getRatios() {
            return ratios;
        }

        public Integer getChange() {
            return change;
        }
    }

    /** FR-46 합격자 참고 루트의 한 단계. */
    public record BenchmarkTier(String tier, String label, List<String> items) {
        public String getTier() {
            return tier;
        }

        public String getLabel() {
            return label;
        }

        public List<String> getItems() {
            return items;
        }
    }

    /** FR-48 분야(행) × 요구 수준(열) 약점 히트맵. weakest는 가장 많이 부족한 칸 설명(없으면 null). */
    public record HeatmapView(List<String> levels, List<HeatRow> rows, int missingTotal, String weakest) {
        public List<String> getLevels() {
            return levels;
        }

        public List<HeatRow> getRows() {
            return rows;
        }

        public int getMissingTotal() {
            return missingTotal;
        }

        public String getWeakest() {
            return weakest;
        }

        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    public record HeatRow(String category, List<HeatCell> cells) {
        public String getCategory() {
            return category;
        }

        public List<HeatCell> getCells() {
            return cells;
        }
    }

    /** shade: 0(부족 없음) ~ 3(가장 많이 부족). total이 0이면 그 직무가 요구하지 않는 칸. */
    public record HeatCell(int missing, int total, int shade) {
        public int getMissing() {
            return missing;
        }

        public int getTotal() {
            return total;
        }

        public int getShade() {
            return shade;
        }
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public PeerView getPeer() {
        return peer;
    }

    public void setPeer(PeerView peer) {
        this.peer = peer;
    }

    public TrendView getTrend() {
        return trend;
    }

    public void setTrend(TrendView trend) {
        this.trend = trend;
    }

    public List<BenchmarkTier> getBenchmark() {
        return benchmark;
    }

    public void setBenchmark(List<BenchmarkTier> benchmark) {
        this.benchmark = benchmark;
    }

    public HeatmapView getHeatmap() {
        return heatmap;
    }

    public void setHeatmap(HeatmapView heatmap) {
        this.heatmap = heatmap;
    }
}
