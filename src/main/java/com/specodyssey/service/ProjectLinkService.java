package com.specodyssey.service;

import com.specodyssey.dto.ProjectLinkDto;
import com.specodyssey.util.UrlRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 프로젝트 기타 링크(블로그 글, 발표 영상, 노션 등)의 검증·정리.
 * 프로젝트당 최대 MAX_LINKS개, 이름은 선택(MAX_LABEL_LENGTH자), 주소는 http/https만 받는다.
 */
public final class ProjectLinkService {

    public static final int MAX_LINKS = 5;
    public static final int MAX_LABEL_LENGTH = 50;

    private ProjectLinkService() {
    }

    /**
     * 입력 줄을 검증하고 정리한다 — 이름·주소를 앞뒤 공백 없이 다듬고, 둘 다 빈 줄은 버리고, 같은 주소는 첫 번째만 남긴다.
     * @throws IllegalArgumentException 사용자에게 그대로 보여줄 메시지
     */
    public static List<ProjectLinkDto> normalize(List<ProjectLinkDto> raw) {
        List<ProjectLinkDto> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ProjectLinkDto link : raw) {
            String label = trimToNull(link.getLabel());
            String url = trimToNull(link.getUrl());
            if (label == null && url == null) {
                continue;
            }
            if (url == null) {
                throw new IllegalArgumentException("링크 \"" + label + "\"의 주소를 입력해주세요.");
            }
            if (label != null && label.length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("링크 이름은 " + MAX_LABEL_LENGTH + "자 이내로 적어주세요.");
            }
            UrlRules.requireWebUrlIfPresent(url, "기타 링크");
            if (seen.add(url)) {
                result.add(new ProjectLinkDto(label, url));
            }
        }
        if (result.size() > MAX_LINKS) {
            throw new IllegalArgumentException("기타 링크는 프로젝트당 최대 " + MAX_LINKS + "개까지 등록할 수 있습니다.");
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
