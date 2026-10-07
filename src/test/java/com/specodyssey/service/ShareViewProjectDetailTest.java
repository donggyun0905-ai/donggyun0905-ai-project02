package com.specodyssey.service;

import com.specodyssey.dto.ProjectDocumentItemDto;
import com.specodyssey.dto.ShareViewDto.SubmittedDoc;
import com.specodyssey.dto.ShareViewDto.TechNote;
import com.specodyssey.dto.ShareViewDto.TimelineItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 면접관 뷰 프로젝트 상세 — DB 없이 도는 규칙 테스트. */
class ShareViewProjectDetailTest {

    @Test
    void 갖춘_서류는_제출한_것만_화면_순서대로_보여주고_파일은_공개_전까지_열지_않는다() {
        List<SubmittedDoc> docs = ShareViewService.submittedDocs(List.of(
                item("SCREENSHOT", "SUBMITTED", 12L),
                item("API_SPEC", "NOT_APPLICABLE", null),
                item("README", "SUBMITTED", 11L)));

        assertEquals(List.of("README / 프로젝트 소개서", "실행 화면 캡처"),
                docs.stream().map(SubmittedDoc::getLabel).collect(Collectors.toList()));
        assertEquals(11L, docs.get(0).getSourceDocumentId());
        // 서비스가 share()를 부르기 전(= 프로젝트 서류 비공개 링크)에는 화면에 문서 id가 없다
        assertNull(docs.get(0).getDocumentId());
    }

    @Test
    void 화면_안_미리보기는_PDF와_이미지만() {
        assertEquals("pdf", ShareViewService.previewType("readme.PDF"));
        assertEquals("image", ShareViewService.previewType("screen.png"));
        assertNull(ShareViewService.previewType("erd.docx"));
        assertNull(ShareViewService.previewType("page.html"));
    }

    @Test
    void 회고도_기술_설명도_서류도_없으면_상세_보기를_띄우지_않는다() {
        TimelineItem item = new TimelineItem("2026-01-01 ~ 진행 중", "프로젝트", "제목", null);
        assertFalse(item.isHasProjectDetail());

        item.setTechNotes(List.of(new TechNote("Java", "도메인 로직 구현")));
        assertTrue(item.isHasProjectDetail());
    }

    @Test
    void 팀_규모와_역할은_한_줄로_보여준다() {
        TimelineItem item = new TimelineItem("2026-01-01 ~ 진행 중", "프로젝트", "제목", null);
        assertNull(item.getTeamText());

        item.setTeamSize(4);
        assertEquals("4인 팀", item.getTeamText());
        item.setMyRole("백엔드 · DB 설계");
        assertEquals("4인 팀 · 백엔드 · DB 설계", item.getTeamText());
        item.setTeamSize(1);
        assertEquals("개인 프로젝트 · 백엔드 · DB 설계", item.getTeamText());
        item.setTeamSize(null);
        assertEquals("백엔드 · DB 설계", item.getTeamText());
    }

    private static ProjectDocumentItemDto item(String docType, String status, Long documentId) {
        ProjectDocumentItemDto dto = new ProjectDocumentItemDto();
        dto.setDocType(docType);
        dto.setStatus(status);
        dto.setDocumentId(documentId);
        return dto;
    }
}
