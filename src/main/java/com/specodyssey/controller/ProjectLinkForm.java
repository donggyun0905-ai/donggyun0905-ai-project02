package com.specodyssey.controller;

import com.specodyssey.dto.ProjectLinkDto;
import com.specodyssey.service.ProjectLinkService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 프로젝트 기타 링크 입력칸 읽기 — linkLabel_0..4 / linkUrl_0..4 (로드맵 제출 폼과 프로필 프로젝트 폼이 같이 쓴다).
 */
final class ProjectLinkForm {

    private ProjectLinkForm() {
    }

    /**
     * @return 입력칸이 폼에 있었으면(linkUrl_0 파라미터가 존재) 읽은 줄 목록(비어 있을 수 있음 = 모두 지움),
     *         폼에 입력칸 자체가 없었으면 null(= 기존 링크를 건드리지 않는다)
     */
    static List<ProjectLinkDto> parse(HttpServletRequest req) {
        if (req.getParameter("linkUrl_0") == null) {
            return null;
        }
        List<ProjectLinkDto> links = new ArrayList<>();
        for (int i = 0; i < ProjectLinkService.MAX_LINKS; i++) {
            String url = req.getParameter("linkUrl_" + i);
            String label = req.getParameter("linkLabel_" + i);
            if (url != null || label != null) {
                links.add(new ProjectLinkDto(label, url));
            }
        }
        return links;
    }
}
