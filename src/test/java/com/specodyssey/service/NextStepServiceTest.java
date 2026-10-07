package com.specodyssey.service;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.NextStepService.Guide;
import com.specodyssey.service.NextStepService.Step;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 다음에 할 일 안내 (FR-114 빈 상태). DB 없이 규칙만 검사한다.
 * 화면마다 "아직 없습니다"만 보이고 무엇을 해야 채워지는지는 안 알려주던 문제를 고친 것이다.
 */
class NextStepServiceTest {

    @Test
    void 갓_가입한_사람에게는_프로필_채우기를_먼저_안내한다() {
        Guide guide = NextStepService.build(false, false, false, false, false);

        assertNotNull(guide.getCurrent());
        assertEquals("프로필 기본 정보 채우기", guide.getCurrent().getTitle());
        assertEquals("/profile", guide.getCurrent().getPath());
        assertEquals(0, guide.getDoneCount());
        assertEquals(5, guide.getTotalCount());
        assertEquals(0, guide.getPercent());
        assertFalse(guide.isAllDone());
    }

    @Test
    void 지금_할_일은_첫_미완료_단계_하나뿐이다() {
        Guide guide = NextStepService.build(true, true, false, false, false);

        assertEquals("목표 직무 정하기", guide.getCurrent().getTitle());
        assertEquals(1, guide.getSteps().stream().filter(Step::isCurrent).count(),
                "강조는 한 곳만 — 두 곳이면 무엇부터 할지 또 고민하게 된다");
        assertEquals(2, guide.getDoneCount());
        assertEquals(40, guide.getPercent());
    }

    @Test
    void 앞_단계를_건너뛴_기존_가입자도_자기_자리에서_이어간다() {
        // 설문 없이 프로필에서 목표 직무만 고른 사람 — 기술은 아직 안 넣었다
        Guide guide = NextStepService.build(true, false, true, false, false);

        assertEquals("할 수 있는 기술 적기", guide.getCurrent().getTitle(),
                "건너뛴 단계가 있으면 거기서부터 이어야 한다");
        assertEquals(List.of("프로필 기본 정보 채우기", "목표 직무 정하기"),
                guide.getSteps().stream().filter(Step::isDone).map(Step::getTitle).collect(Collectors.toList()));
    }

    @Test
    void 여정을_다_걸은_사람에게는_안내를_띄우지_않는다() {
        Guide guide = NextStepService.build(true, true, true, true, true);

        assertNull(guide.getCurrent());
        assertTrue(guide.isAllDone(), "할 일이 없는데 카드가 남아 있으면 방해만 된다");
        assertEquals(100, guide.getPercent());
    }

    @Test
    void 기본_정보는_전공과_학년이_둘_다_있어야_채운_것으로_본다() {
        assertFalse(NextStepService.hasBasicProfile(user(null, null)));
        assertFalse(NextStepService.hasBasicProfile(user("컴퓨터공학과", null)));
        assertFalse(NextStepService.hasBasicProfile(user(null, "4학년")));
        assertFalse(NextStepService.hasBasicProfile(user("  ", "4학년")), "공백만 넣은 것은 안 채운 것이다");
        assertTrue(NextStepService.hasBasicProfile(user("컴퓨터공학과", "4학년")));
    }

    @Test
    void 안내하는_모든_단계에_갈_곳과_버튼_문구가_있다() {
        for (Step step : NextStepService.build(false, false, false, false, false).getSteps()) {
            assertTrue(step.getPath() != null && step.getPath().startsWith("/"), step.getTitle());
            assertFalse(step.getActionLabel() == null || step.getActionLabel().isBlank(), step.getTitle());
            assertFalse(step.getHint() == null || step.getHint().isBlank(), step.getTitle());
        }
    }

    private static UserDto user(String major, String grade) {
        UserDto user = new UserDto();
        user.setMajor(major);
        user.setGrade(grade);
        return user;
    }
}
