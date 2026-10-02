package com.specodyssey.controller;

import com.specodyssey.dto.DocumentDto;
import com.specodyssey.service.ProjectSubmission;
import com.specodyssey.util.FileStorageUtil;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 프로젝트 제출 폼 읽기 — 폼 필드 이름 규칙(doc_/na_/techName_/files)과 실패 시 파일 정리. */
class RoadmapSubmissionFormTest {

    private FakeWeb.Request form() {
        return FakeWeb.request().post("/roadmap").param("title", " 내 프로젝트 ").param("description", "설명")
                .param("techStack", "Java");
    }

    private void cleanup(ProjectSubmission submission) {
        RoadmapSubmissionForm.deleteNewFiles(submission);
    }

    @Test
    void 기본_필드와_링크_회고_날짜를_읽고_앞뒤_공백은_뗀다() throws Exception {
        FakeWeb.Request req = form().param("repoUrl", " https://github.com/a/b ").param("deployUrl", "")
                .param("retrospective", "회고").param("startDate", "2026-01-02").param("endDate", "2026-03-04");

        ProjectSubmission s = RoadmapSubmissionForm.buildSubmission(req.http());

        assertEquals("내 프로젝트", s.getProject().getTitle());
        assertEquals("https://github.com/a/b", s.getProject().getRepoUrl());
        assertNull(s.getProject().getDeployUrl(), "빈 값은 null");
        assertEquals("회고", s.getProject().getRetrospective());
        assertEquals("2026-01-02", s.getProject().getStartDate().toString());
        assertTrue(s.getDocs().isEmpty());
    }

    @Test
    void 필수_필드가_비었거나_날짜_형식이_틀리면_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> RoadmapSubmissionForm.buildSubmission(
                FakeWeb.request().post("/roadmap").param("title", "제목").param("description", "설명").http()));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RoadmapSubmissionForm.buildSubmission(form().param("startDate", "어제").http()));
        assertEquals("날짜 형식이 올바르지 않습니다.", e.getMessage());
    }

    @Test
    void 문서_종류별_파일과_해당_없음_기술_설명서_기타_증빙을_읽는다() throws Exception {
        FakeWeb.Request req = form()
                .file("doc_README", "README.md", "소개".getBytes())
                .file("doc_SCREENSHOT", "화면.png", new byte[] {1, 2, 3})
                .param("na_API_SPEC", "on")
                .param("techName_0", "Java").param("techDesc_0", "서버 구현").param("techConsent_0", "on")
                .param("techName_1", "혼자있는이름") // 설명이 없으면 건너뛴다
                .file("files", "기타.zip", new byte[] {9});

        ProjectSubmission s = RoadmapSubmissionForm.buildSubmission(req.http());
        try {
            assertEquals("README.md", s.getDocs().get("README").getFile().getOriginalName());
            assertEquals("text/plain; charset=UTF-8", s.getDocs().get("README").getFile().getMimeType());
            assertEquals("image/png", s.getDocs().get("SCREENSHOT").getFile().getMimeType());
            assertTrue(s.getDocs().get("API_SPEC").isNotApplicable());
            assertFalse(s.getDocs().containsKey("PLANNING"), "아무것도 안 고른 종류는 슬롯이 없다");
            assertEquals(1, s.getTechNotes().size());
            assertEquals("Java", s.getTechNotes().get(0).getTechName());
            assertTrue(s.getTechNotes().get(0).isConsentForTraining());
            assertEquals(1, s.getExtraFiles().size());
            for (DocumentDto file : s.allNewFiles()) {
                assertTrue(Files.exists(Paths.get(file.getFilePath())), "읽는 즉시 디스크에 저장된다");
            }
        } finally {
            cleanup(s);
        }
    }

    @Test
    void 허용되지_않은_형식이_섞이면_이미_저장한_파일까지_지우고_거절한다() throws Exception {
        FakeWeb.Request req = form()
                .file("doc_README", "README.md", "소개".getBytes())
                .file("files", "evil.html", "<script>".getBytes());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RoadmapSubmissionForm.buildSubmission(req.http()));
        assertTrue(e.getMessage().contains("evil.html"));
        // 이 테스트가 막 만든 README 사본이 업로드 폴더에 남지 않았는지 확인한다(기본 폴더가 아니면 이 확인은 건너뛴다)
        java.nio.file.Path uploads = Paths.get(System.getProperty("user.home"), "spec-odyssey-uploads");
        if (Files.isDirectory(uploads)) {
            try (var files = Files.list(uploads)) {
                long leftovers = files.filter(p -> p.toString().endsWith(".md")).filter(p -> {
                    try {
                        return Files.readString(p).equals("소개")
                                && Files.getLastModifiedTime(p).toMillis() > System.currentTimeMillis() - 5_000;
                    } catch (Exception ex) {
                        return false;
                    }
                }).count();
                assertEquals(0, leftovers);
            }
        }
    }

    @Test
    void 기타_링크_입력칸이_폼에_없으면_null이고_있으면_줄_목록이다() throws Exception {
        assertNull(RoadmapSubmissionForm.buildSubmission(form().http()).getLinks(), "입력칸 자체가 없으면 기존 링크를 건드리지 않는다");

        FakeWeb.Request req = form().param("linkLabel_0", "블로그").param("linkUrl_0", "https://blog.example.com")
                .param("linkLabel_1", "").param("linkUrl_1", "")
                .param("linkLabel_2", "영상").param("linkUrl_2", "https://youtu.be/x");
        var links = RoadmapSubmissionForm.buildSubmission(req.http()).getLinks();
        assertEquals(3, links.size(), "빈 줄도 일단 읽고, 걸러내는 건 서비스가 한다");
        assertEquals("블로그", links.get(0).getLabel());
        assertEquals("https://youtu.be/x", links.get(2).getUrl());

        // 입력칸은 있었지만 전부 비운 경우 — 빈 목록(= 링크를 모두 지움)
        var cleared = RoadmapSubmissionForm.buildSubmission(form().param("linkUrl_0", "").http()).getLinks();
        assertEquals(1, cleared.size());
        assertEquals(0, com.specodyssey.service.ProjectLinkService.normalize(cleared).size());
    }

    @Test
    void trimToNull은_공백만_있으면_null이다() {
        assertNull(RoadmapSubmissionForm.trimToNull("   "));
        assertNull(RoadmapSubmissionForm.trimToNull(null));
        assertEquals("a", RoadmapSubmissionForm.trimToNull(" a "));
        assertNull(RoadmapSubmissionForm.parseDate(" "));
        assertTrue(FileStorageUtil.isAllowedFile("README.md"));
    }
}
