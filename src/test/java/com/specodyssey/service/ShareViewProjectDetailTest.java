package com.specodyssey.service;

import com.specodyssey.dto.ProjectDocumentItemDto;
import com.specodyssey.dto.ShareViewDto;
import com.specodyssey.dto.ShareViewDto.SubmittedDoc;
import com.specodyssey.dto.ShareViewDto.TechNote;
import com.specodyssey.dto.ShareViewDto.TimelineItem;
import org.junit.jupiter.api.Test;

import com.specodyssey.util.FileStorageUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // 2026-10-07: 타임라인에 프로젝트와 섞여 시간순으로만 들어가 "자격증이 몇 개인지"를 훑을 수 없었다.
    @Test
    void 보유_스펙은_자격증_어학_수상_경험_순서로_묶인다() {
        Map<String, List<TimelineItem>> byType = new LinkedHashMap<>();
        byType.put("경험", List.of(spec("백엔드 인턴")));
        byType.put("자격증", List.of(spec("정보처리기사"), spec("SQLD")));
        byType.put("어학", List.of(spec("TOEIC 900")));

        Map<String, List<TimelineItem>> ordered = ShareViewService.orderedByType(byType);

        assertEquals(List.of("자격증", "어학", "경험"), List.copyOf(ordered.keySet()));
        assertEquals(2, ordered.get("자격증").size());
    }

    @Test
    void 종류_라벨을_모르는_스펙도_빠뜨리지_않는다() {
        Map<String, List<TimelineItem>> byType = new LinkedHashMap<>();
        byType.put("자격증", List.of(spec("정보처리기사")));
        byType.put("MILITARY", List.of(spec("병역 특례"))); // spec_type은 자유 값이다

        Map<String, List<TimelineItem>> ordered = ShareViewService.orderedByType(byType);

        assertEquals(List.of("자격증", "MILITARY"), List.copyOf(ordered.keySet()));
    }

    // 업로드 폴더는 서버 PC마다 따로다 — DB에는 행만 있고 이 서버에 파일이 없으면 열리지 않는 미리보기를
    // 그리는 대신 이유를 알려야 한다 (2026-10-07 면접관 이력 보기에서 PDF가 안 보였다).
    @Test
    void 이_서버에_파일이_없는_서류는_열기_링크를_만들지_않고_이름만_남긴다() {
        SubmittedDoc doc = ShareViewService.submittedDocs(List.of(item("README", "SUBMITTED", 11L))).get(0);
        assertFalse(doc.isFileMissing());

        doc.markMissing("readme.pdf");

        assertTrue(doc.isFileMissing());
        assertEquals("readme.pdf", doc.getFileName());
        assertNull(doc.getDocumentId(), "문서 id가 있으면 화면이 열기·내려받기 링크를 만든다");
        assertNull(doc.getPreviewType(), "미리보기도 그리면 안 된다");
    }

    @Test
    void 파일이_없으면_PDF라도_화면_안_미리보기를_켜지_않는다() {
        ShareViewDto view = new ShareViewDto();
        view.setResumeFileName("이력서.pdf");

        assertFalse(view.isResumePdf(), "파일이 없는데 미리보기를 켜면 빈 iframe만 남는다");

        view.setResumeFileReadable(true);
        assertTrue(view.isResumePdf());
    }

    @Test
    void 읽을_수_없는_경로는_없는_것으로_본다() throws Exception {
        assertFalse(FileStorageUtil.isReadable(null));
        assertFalse(FileStorageUtil.isReadable("  "));
        assertFalse(FileStorageUtil.isReadable("C:\\Users\\그런사람없음\\없는파일.pdf"));

        Path real = Files.createTempFile("share-view-", ".pdf");
        try {
            assertTrue(FileStorageUtil.isReadable(real.toString()));
        } finally {
            Files.deleteIfExists(real);
        }
        assertFalse(FileStorageUtil.isReadable(real.toString()), "지워진 뒤에는 없는 것으로 봐야 한다");
    }

    private static TimelineItem spec(String title) {
        return new TimelineItem("2026-01-01", "자격증", title, null);
    }

    private static ProjectDocumentItemDto item(String docType, String status, Long documentId) {
        ProjectDocumentItemDto dto = new ProjectDocumentItemDto();
        dto.setDocType(docType);
        dto.setStatus(status);
        dto.setDocumentId(documentId);
        return dto;
    }
}
