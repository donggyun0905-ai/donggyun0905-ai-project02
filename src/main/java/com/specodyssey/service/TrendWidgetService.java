package com.specodyssey.service;

import com.specodyssey.dao.TrendCollectDao;
import com.specodyssey.dto.TrendTechDto;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "오늘의 트렌드 기술" 사이드 위젯에 무엇을 보여줄지 정한다. 관련 요구사항: FR-54·55
 *
 * 목표 직무에 확실히 맞는 것만 보여주는 게 원칙이다(2026-10-03 사용자 요청).
 *   1) 그 직무에 연결된 트렌드 중 관련도 MIN_RELEVANCE 이상
 *   2) 모자라면 같은 직무 계열(JOB.job_category)의 다른 직무 트렌드로 채운다
 *   3) 그래도 없으면 엉뚱한 기술을 보여주지 않고 빈 목록(화면은 "아직 없음" 안내)
 * 목표 직무가 아직 없을 때(갓 가입)만 직무와 상관없는 최근 트렌드를 보여준다.
 */
public class TrendWidgetService {

    // LLM이 매긴 직무 관련도(0~1). 0.6~0.7대에는 "API 개발자 ↔ Git SHA-256"처럼 억지로 이은 것이 섞여 있어 잘라낸다.
    static final double MIN_RELEVANCE = 0.8;

    public enum Source { JOB, JOB_AND_CATEGORY, CATEGORY, NONE, GENERAL }

    public record TrendWidget(List<TrendTechDto> items, Source source) {
    }

    private final TrendCollectDao trendDao;

    public TrendWidgetService() {
        this(new TrendCollectDao());
    }

    TrendWidgetService(TrendCollectDao trendDao) {
        this.trendDao = trendDao;
    }

    public TrendWidget forJob(Long desiredJobId, int size) throws SQLException {
        if (desiredJobId == null) {
            return new TrendWidget(trendDao.findRecent(size), Source.GENERAL);
        }
        List<TrendTechDto> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TrendTechDto tech : trendDao.findRelevantByJobId(desiredJobId, MIN_RELEVANCE, size * 3)) {
            if (items.size() < size && seen.add(key(tech))) {
                items.add(tech);
            }
        }
        int ownCount = items.size();
        if (ownCount >= size) {
            return new TrendWidget(items, Source.JOB);
        }
        for (TrendTechDto sibling : trendDao.findRelevantBySiblingJobs(desiredJobId, MIN_RELEVANCE, size * 3)) {
            if (items.size() >= size) {
                break;
            }
            if (seen.add(key(sibling))) {
                items.add(sibling);
            }
        }
        Source source = items.isEmpty() ? Source.NONE
                : ownCount == 0 ? Source.CATEGORY
                : items.size() > ownCount ? Source.JOB_AND_CATEGORY
                : Source.JOB;
        return new TrendWidget(items, source);
    }

    // 같은 기술이 다른 날 다시 수집돼 행이 둘일 수 있어 이름으로 중복을 거른다
    private static String key(TrendTechDto tech) {
        return tech.getTechName() == null ? String.valueOf(tech.getId()) : tech.getTechName().trim().toLowerCase();
    }
}
