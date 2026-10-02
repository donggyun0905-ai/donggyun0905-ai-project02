package com.specodyssey.service;

import com.specodyssey.dao.DdayAlertDao;
import com.specodyssey.dto.DdayAlertDto;
import com.specodyssey.dto.DdayItemDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * D-day 일정 조회·등록·수정·삭제. 관련 요구사항: FR-71 · 72
 * 일정은 전부 사용자가 직접 넣는다 — 자격증 시험 일정은 기관마다 출처가 달라 자동 등록하지 않는다.
 */
public class DdayService {

    static final int URGENT_DAYS = 7;          // FR-72 이 일수 안으로 들어오면 마감 임박으로 강조한다
    private static final int TITLE_MAX_LENGTH = 100; // DDAY_ALERT.title VARCHAR(100)

    private static final Map<String, String> TYPE_LABELS = Map.of(
            "CERT", "자격증", "RECRUIT", "공채", "CUSTOM", "기타");

    private final DdayAlertDao ddayAlertDao = new DdayAlertDao();

    /** 다가오는 일정을 마감이 가까운 순으로, 그 뒤에 지난 일정을 최근 것부터 돌려준다. */
    public List<DdayItemDto> listItems(Long userId, LocalDate today) throws SQLException {
        List<DdayItemDto> upcoming = new ArrayList<>();
        List<DdayItemDto> past = new ArrayList<>();
        // findByUserId는 target_date 오름차순
        for (DdayAlertDto alert : ddayAlertDao.findByUserId(userId)) {
            DdayItemDto item = toItem(alert, today);
            if (item.getDaysLeft() >= 0) {
                upcoming.add(item);
            } else {
                past.add(0, item);
            }
        }
        upcoming.addAll(past);
        return upcoming;
    }

    /** FR-72 화면 상단에 강조할 일정 — 마감 임박 구간에 든 가장 가까운 일정. 없으면 null. */
    public DdayItemDto findMostUrgent(List<DdayItemDto> items) {
        for (DdayItemDto item : items) {
            if ("URGENT".equals(item.getUrgency())) {
                return item;
            }
        }
        return null;
    }

    /** 사이드 메뉴 배지에 쓸, 아직 지나지 않은 일정 중 가장 가까운 것. 없으면 null. */
    public DdayItemDto findNearest(Long userId, LocalDate today) throws SQLException {
        List<DdayItemDto> items = listItems(userId, today);
        // listItems는 다가오는 일정을 앞에 두므로 첫 항목이 지난 일정이면 다가오는 일정이 없는 것이다
        return (items.isEmpty() || items.get(0).getDaysLeft() < 0) ? null : items.get(0);
    }

    /**
     * FR-71 일정 직접 추가.
     * @throws IllegalArgumentException 입력이 잘못된 경우 — 메시지를 그대로 화면에 보여준다
     */
    public Long addItem(Long userId, String title, LocalDate targetDate, String alertType, LocalDate today)
            throws SQLException {
        validate(title, targetDate, alertType);
        if (targetDate.isBefore(today)) {
            throw new IllegalArgumentException("오늘 이후의 날짜를 선택해주세요.");
        }

        DdayAlertDto alert = new DdayAlertDto();
        alert.setUserId(userId);
        alert.setTitle(title.trim());
        alert.setTargetDate(targetDate);
        alert.setAlertType(alertType);
        return ddayAlertDao.insert(alert);
    }

    /**
     * 일정 수정. 지난 날짜로도 바꿀 수 있다(이미 지난 일정의 제목만 고치는 경우).
     * @throws IllegalArgumentException 입력이 잘못됐거나 본인 일정이 아닌 경우
     */
    public void updateItem(Long userId, Long alertId, String title, LocalDate targetDate, String alertType)
            throws SQLException {
        validate(title, targetDate, alertType);

        DdayAlertDto target = null;
        for (DdayAlertDto alert : ddayAlertDao.findByUserId(userId)) {
            if (alert.getId().equals(alertId)) {
                target = alert;
            }
        }
        // 남의 일정이거나 지워진 일정이면 본인 목록에 없다
        if (target == null) {
            throw new IllegalArgumentException("수정할 수 없는 일정입니다.");
        }

        target.setTitle(title.trim());
        target.setTargetDate(targetDate);
        target.setAlertType(alertType);
        try (Connection conn = DBUtil.getConnection()) {
            ddayAlertDao.update(conn, target, userId);
        }
    }

    private static void validate(String title, LocalDate targetDate, String alertType) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }
        if (title.trim().length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("제목은 " + TITLE_MAX_LENGTH + "자 이내로 입력해주세요.");
        }
        if (targetDate == null) {
            throw new IllegalArgumentException("날짜를 선택해주세요.");
        }
        if (alertType == null || !TYPE_LABELS.containsKey(alertType)) {
            throw new IllegalArgumentException("종류를 다시 선택해주세요.");
        }
    }

    // 남의 일정 id면 아무 일도 일어나지 않는다
    public void deleteItem(Long userId, Long alertId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            ddayAlertDao.delete(conn, alertId, userId);
        }
    }

    static DdayItemDto toItem(DdayAlertDto alert, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, alert.getTargetDate());

        DdayItemDto item = new DdayItemDto();
        item.setId(alert.getId());
        item.setTitle(alert.getTitle());
        item.setTargetDate(alert.getTargetDate());
        item.setAlertType(alert.getAlertType());
        item.setTypeLabel(TYPE_LABELS.getOrDefault(alert.getAlertType(), alert.getAlertType()));
        item.setDaysLeft(daysLeft);
        if (daysLeft < 0) {
            item.setDdayText("지남");
            item.setUrgency("PAST");
        } else {
            item.setDdayText(daysLeft == 0 ? "D-DAY" : "D-" + daysLeft);
            item.setUrgency(daysLeft <= URGENT_DAYS ? "URGENT" : "UPCOMING");
        }
        return item;
    }
}
