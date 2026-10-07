package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.SkillAliasDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.service.ProfileInputChecker.Result;
import com.specodyssey.service.ProfileInputChecker.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 공유 DB의 SKILL·SKILL_ALIAS·CERTIFICATION 시드를 그대로 읽는다(쓰지 않음).
 * 임베딩 모델이 없는 PC에서도 통과해야 한다 — 아래 기대값은 이름·별칭·편집 거리만으로 나오는 것들이다.
 */
class ProfileInputCheckerTest {

    private final ProfileInputChecker checker = new ProfileInputChecker();

    @Test
    void 엉터리_기술명은_막고_저장도_거절한다() throws Exception {
        for (String input : List.of("sdafdsafdsa", "ㅁㄴㅇㄹ", "qwerty", "1234")) {
            assertEquals(Status.GIBBERISH, checker.checkSkill(input).status(), input);
            assertTrue(checker.rejectsSkill(input), input);
        }
    }

    @Test
    void 정확한_기술명은_안내_없이_통과한다() throws Exception {
        Result result = checker.checkSkill("Java");
        assertEquals(Status.OK, result.status());
        assertNull(result.message());
        assertFalse(checker.rejectsSkill("Java"));
    }

    @Test
    void 한글_별칭은_정식_이름으로_인식했다고_알려준다() throws Exception {
        Result result = checker.checkSkill("자바");
        assertEquals(Status.OK, result.status());
        assertTrue(result.message().contains("Java"));
    }

    @Test
    void 기술명_오타는_맞는_이름을_제안한다() throws Exception {
        assertEquals(List.of("Python"), checker.checkSkill("Pyhton").suggestions());
        assertEquals(List.of("Kubernetes"), checker.checkSkill("Kubernetis").suggestions());
        assertEquals(Status.SUGGEST, checker.checkSkill("Djnago").status());
    }

    @Test
    void 목록에_없는_말이_되는_이름은_막지_않는다() throws Exception {
        Result result = checker.checkSkill("한국어 맞춤법 검사기 만들기");
        assertFalse(result.status() == Status.GIBBERISH);
        assertFalse(checker.rejectsSkill("한국어 맞춤법 검사기 만들기"));
    }

    @Test
    void 시드에_있는_기술_별칭_자격증_이름은_하나도_거절하지_않는다() throws Exception {
        SkillCatalog.Snapshot catalog = SkillCatalog.current();
        for (SkillDto skill : catalog.skills()) {
            assertFalse(checker.rejectsSkill(skill.getSkillName()), skill.getSkillName());
        }
        for (SkillAliasDto alias : catalog.aliases()) {
            assertFalse(checker.rejectsSkill(alias.getAliasName()), alias.getAliasName());
        }
        for (CertificationDto cert : new CertificationDao().findAll()) {
            assertFalse(checker.rejectsSpec("CERT", cert.getCertName()), cert.getCertName());
        }
    }

    @Test
    void 자격증_줄임말은_정식_이름을_제안한다() throws Exception {
        Result result = checker.checkSpec("CERT", "정처기");
        assertEquals(Status.SUGGEST, result.status());
        assertEquals(List.of("정보처리기사"), result.suggestions());
        assertEquals(List.of("컴퓨터활용능력 1급", "컴퓨터활용능력 2급"), checker.checkSpec("CERT", "컴활").suggestions());
        assertFalse(checker.rejectsSpec("CERT", "정처기"));
        // 자격증 칸에 점수까지 쓴 경우
        assertEquals(List.of("TOEIC"), checker.checkSpec("CERT", "토익 850").suggestions());
    }

    @Test
    void 자격증_오타와_앞부분만_쓴_이름은_후보를_보여준다() throws Exception {
        assertEquals("정보처리기사", checker.checkSpec("CERT", "정보처리기싸").suggestions().get(0));
        assertTrue(checker.checkSpec("CERT", "리눅스마스터").suggestions().contains("리눅스마스터 1급"));
    }

    @Test
    void 띄어쓰기만_다른_자격증은_인식한다() throws Exception {
        Result result = checker.checkSpec("CERT", "정보처리 기사");
        assertEquals(Status.OK, result.status());
        assertTrue(result.message().contains("정보처리기사"));
    }

    @Test
    void 어학은_점수를_떼고_시험_이름만_본다() throws Exception {
        assertEquals(Status.OK, checker.checkSpec("LANGUAGE", "토익 850").status());
        assertEquals(Status.OK, checker.checkSpec("LANGUAGE", "OPIc IH").status());
        // 목록에 없는 어학 시험도 흔하다 — 안내하지 않는다
        Result jlpt = checker.checkSpec("LANGUAGE", "JLPT N1");
        assertEquals(Status.OK, jlpt.status());
        assertNull(jlpt.message());
    }

    @Test
    void 수상은_엉터리_글자만_막는다() throws Exception {
        assertEquals(Status.OK, checker.checkSpec("AWARD", "교내 해커톤 대상").status());
        assertEquals(Status.GIBBERISH, checker.checkSpec("AWARD", "ㅁㄴㅇㄹ").status());
        assertTrue(checker.rejectsSpec("AWARD", "sdafdsafdsa"));
    }
}
