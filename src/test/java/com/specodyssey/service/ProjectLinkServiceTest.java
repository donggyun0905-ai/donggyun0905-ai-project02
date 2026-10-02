package com.specodyssey.service;

import com.specodyssey.dto.ProjectLinkDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectLinkServiceTest {

    private static ProjectLinkDto link(String label, String url) {
        return new ProjectLinkDto(label, url);
    }

    @Test
    void 공백을_다듬고_빈_줄은_버리고_같은_주소는_첫_번째만_남긴다() {
        List<ProjectLinkDto> result = ProjectLinkService.normalize(List.of(
                link(" 블로그 글 ", " https://blog.example.com/a "),
                link("", ""),
                link(null, null),
                link("중복", "https://blog.example.com/a"),
                link("  ", "https://youtu.be/xyz")));

        assertEquals(2, result.size());
        assertEquals("블로그 글", result.get(0).getLabel());
        assertEquals("https://blog.example.com/a", result.get(0).getUrl());
        assertEquals(null, result.get(1).getLabel(), "이름이 공백뿐이면 비운다");
        assertEquals("youtu.be", result.get(1).getDisplayName(), "이름이 없으면 주소의 호스트를 보여준다");
    }

    @Test
    void 주소가_웹_주소가_아니면_거절한다() {
        for (String bad : new String[] {"javascript:alert(1)", "data:text/html,x", "ftp://a.com", "그냥 글자"}) {
            assertThrows(IllegalArgumentException.class, () -> ProjectLinkService.normalize(List.of(link("x", bad))), bad);
        }
    }

    @Test
    void 이름만_있고_주소가_없으면_알려준다() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ProjectLinkService.normalize(List.of(link("발표 영상", " "))));
        assertTrue(e.getMessage().contains("발표 영상") && e.getMessage().contains("주소"));
    }

    @Test
    void 이름이_너무_길거나_링크가_다섯_개를_넘으면_거절한다() {
        assertThrows(IllegalArgumentException.class,
                () -> ProjectLinkService.normalize(List.of(link("가".repeat(51), "https://a.com"))));
        List<ProjectLinkDto> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(link("l" + i, "https://example.com/" + i));
        }
        assertThrows(IllegalArgumentException.class, () -> ProjectLinkService.normalize(six));
        assertEquals(5, ProjectLinkService.normalize(six.subList(0, 5)).size());
    }
}
