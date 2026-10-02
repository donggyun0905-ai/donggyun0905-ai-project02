package com.specodyssey.util;

import java.util.Collections;
import java.util.List;

/**
 * 목록을 한 화면에 PAGE 개씩 잘라 보여주기 위한 계산. 계속 늘어나는 목록(서류, 공유 링크, D-day)이 화면을 끝없이 늘리지 않게 한다.
 * 쪽 번호는 1부터이고, 범위를 벗어나면(예: 마지막 쪽의 마지막 항목을 지운 뒤) 가장 가까운 쪽으로 맞춘다.
 * JSP의 EL이 읽으므로 구성요소마다 getter를 둔다(record가 아니라 클래스인 이유 — Tomcat 10.1/11 EL 호환).
 */
public final class Pager<T> {

    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final int total;
    private final int totalPages;

    private Pager(List<T> items, int page, int pageSize, int total, int totalPages) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
        this.totalPages = totalPages;
    }

    public static <T> Pager<T> of(List<T> all, int requestedPage, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize는 1 이상이어야 합니다.");
        }
        int total = all.size();
        int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        int from = (page - 1) * pageSize;
        int to = Math.min(total, from + pageSize);
        List<T> slice = total == 0 ? Collections.emptyList() : List.copyOf(all.subList(from, to));
        return new Pager<>(slice, page, pageSize, total, totalPages);
    }

    /** 요청 파라미터("2" 등)를 쪽 번호로 — 비었거나 숫자가 아니면 1쪽 */
    public static int parsePage(String value) {
        if (value == null) {
            return 1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotal() {
        return total;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isHasPrevious() {
        return page > 1;
    }

    public boolean isHasNext() {
        return page < totalPages;
    }

    /** 쪽이 둘 이상일 때만 쪽 이동을 보여준다 */
    public boolean isNeeded() {
        return totalPages > 1;
    }
}
