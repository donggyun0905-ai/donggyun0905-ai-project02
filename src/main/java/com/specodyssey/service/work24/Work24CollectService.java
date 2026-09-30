package com.specodyssey.service.work24;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 고용24 Open API 3종(직무정보·학과정보·직업정보)을 모아 DB에 반영한다.
 * 호출 경로: Work24Scheduler(매일 00:00, 서버 기동 직후) · ApiUpdater(수동 실행)
 *
 * API 하나가 실패해도 나머지는 진행한다. 실패한 API는 아무것도 덮어쓰지 않아 직전 데이터가 유지된다 (FR-111).
 */
public class Work24CollectService {

    private static final Logger LOG = Logger.getLogger(Work24CollectService.class.getName());

    public enum Target {
        DUTY, MAJOR, JOB;

        Work24Collector newCollector() {
            return switch (this) {
                case DUTY -> new DutyInfoCollector();
                case MAJOR -> new MajorInfoCollector();
                case JOB -> new JobInfoCollector();
            };
        }
    }

    /** API 하나의 실행 결과 */
    public record Outcome(String name, boolean success, String message) {
        @Override
        public String toString() {
            return (success ? "[성공] " : "[실패] ") + name + " — " + message;
        }
    }

    /** 스케줄러용 — 오늘 아직 성공한 적 없는 API만 실행한다 (서버가 자정에 꺼져 있었던 경우 대비). */
    public List<Outcome> collectIfNotYetToday() {
        List<Outcome> outcomes = new ArrayList<>();
        for (Target target : Target.values()) {
            Work24Collector collector = target.newCollector();
            try {
                if (new Work24Cache().collectedToday(collector.keyPrefix())) {
                    continue;
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, collector.name() + " 오늘 수집 여부 확인 실패 — 수집을 시도합니다", e);
            }
            outcomes.add(run(collector, false));
        }
        return outcomes;
    }

    /**
     * 지정한 API를 오늘 수집 여부와 상관없이 실행한다.
     * @param dryRun true면 호출·파싱만 하고 DB에 쓰지 않는다 (무엇이 들어갈지 요약만 돌려준다)
     */
    public List<Outcome> collect(List<Target> targets, boolean dryRun) {
        List<Outcome> outcomes = new ArrayList<>();
        for (Target target : targets) {
            outcomes.add(run(target.newCollector(), dryRun));
        }
        return outcomes;
    }

    private Outcome run(Work24Collector collector, boolean dryRun) {
        try {
            Work24Collector.Prepared prepared = collector.prepare();
            String message = dryRun ? "(dry-run, DB 반영 안 함) " + prepared.summary() : prepared.save();
            LOG.info("고용24 " + collector.name() + " 수집 완료: " + message);
            return new Outcome(collector.name(), true, message);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "고용24 " + collector.name() + " 수집 실패 — 직전 데이터를 유지합니다", e);
            if (!dryRun) {
                recordFailure(collector);
            }
            return new Outcome(collector.name(), false, e.getMessage());
        }
    }

    // 직무정보는 직무별로 이미 실패를 기록한다. 목록형(학과·직업)은 키가 하나라 여기서 남긴다.
    private void recordFailure(Work24Collector collector) {
        String key = collector instanceof MajorInfoCollector ? MajorInfoCollector.REQUEST_KEY
                : collector instanceof JobInfoCollector ? JobInfoCollector.REQUEST_KEY : null;
        if (key == null) {
            return;
        }
        try {
            new Work24Cache().saveFailure(key);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "고용24 실패 기록 저장 실패: " + key, e);
        }
    }
}
