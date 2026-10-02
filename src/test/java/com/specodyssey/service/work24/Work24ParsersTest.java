package com.specodyssey.service.work24;

import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 고용24 응답 파싱 · NCS 기술 추출 단위테스트 (네트워크·DB 없이 실행).
 * 샘플은 2026-09-30 실제 응답에서 떼어 왔다.
 */
class Work24ParsersTest {

    // ---------------------------------------------------------------- 학과정보 213L01

    private static final String MAJOR_XML = """
            <?xml version="1.0" encoding="UTF-8"?><majorsList><total>4</total>
            <majorList><majorGb>1</majorGb><knowDtlSchDptNm>컴퓨터공학과</knowDtlSchDptNm><knowSchDptNm>컴퓨터공학과</knowSchDptNm><empCurtState1Id>5</empCurtState1Id><empCurtState2Id>70</empCurtState2Id></majorList>
            <majorList><majorGb>1</majorGb><knowDtlSchDptNm>컴퓨터소프트웨어공학과</knowDtlSchDptNm><knowSchDptNm>컴퓨터공학과</knowSchDptNm><empCurtState1Id>5</empCurtState1Id><empCurtState2Id>70</empCurtState2Id></majorList>
            <majorList><majorGb>1</majorGb><knowDtlSchDptNm>컴퓨터소프트웨어공학과</knowDtlSchDptNm><knowSchDptNm>컴퓨터공학과</knowSchDptNm><empCurtState1Id>5</empCurtState1Id><empCurtState2Id>70</empCurtState2Id></majorList>
            <majorList><majorGb>2</majorGb><knowDtlSchDptNm>커피바리스타전공</knowDtlSchDptNm><knowSchDptNm>식품/웰빙/여가</knowSchDptNm><empCurtState1Id>A001</empCurtState1Id><empCurtState2Id>10001</empCurtState2Id></majorList>
            </majorsList>""";

    @Test
    void 학과는_학과명으로_묶고_세부학과는_중복없이_모은다() throws ExternalApiException {
        List<MajorInfoCollector.Major> majors = MajorInfoCollector.parse(MAJOR_XML);

        assertEquals(2, majors.size());
        MajorInfoCollector.Major cs = majors.get(0);
        assertEquals("컴퓨터공학과", cs.name());
        assertEquals("5", cs.group());
        assertFalse(cs.unusual());
        // 학과명과 같은 세부학과명은 빼고, 중복 행은 하나로
        assertEquals(List.of("컴퓨터소프트웨어공학과"), cs.details());
        assertTrue(majors.get(1).unusual(), "majorGb=2는 이색학과");
    }

    @Test
    void 잘못된_XML은_파싱_예외() {
        assertThrows(ExternalApiException.class, () -> MajorInfoCollector.parse("<majorsList><majorList>"));
    }

    @Test
    void DTD가_있는_XML은_거부한다_XXE_차단() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<majorsList><majorList><knowSchDptNm>&e;</knowSchDptNm></majorList></majorsList>";
        assertThrows(ExternalApiException.class, () -> MajorInfoCollector.parse(xxe));
    }

    // ---------------------------------------------------------------- 직업정보 212L01

    @Test
    void 직업은_IT_분류코드_13x만_남긴다() throws ExternalApiException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><jobsList><total>3</total>
                <jobList><jobClcd>133</jobClcd><jobClcdNM>소프트웨어 개발자</jobClcdNM><jobCd>K000001106</jobCd><jobNm>웹개발자(웹 프로그래머)</jobNm></jobList>
                <jobList><jobClcd>131</jobClcd><jobClcdNM>컴퓨터하드웨어·통신공학 기술자</jobClcdNM><jobCd>K000000936</jobCd><jobNm>통신기술개발자 </jobNm></jobList>
                <jobList><jobClcd>521</jobClcd><jobClcdNM>여행 서비스원</jobClcdNM><jobCd>K000001093</jobCd><jobNm>여행상품 개발자</jobNm></jobList>
                </jobsList>""";

        List<JobInfoCollector.Job> jobs = JobInfoCollector.parseItJobs(xml);

        assertEquals(List.of("K000001106", "K000000936"), jobs.stream().map(JobInfoCollector.Job::jobCd).toList());
        assertEquals("통신기술개발자", jobs.get(1).jobNm(), "끝 공백은 잘라낸다");
    }

    // ---------------------------------------------------------------- 직무정보 215L01

    private static final String DUTY_JSON = """
            {"result":{
              "생체인식 API 설계":{"job_lrcl_cd":"http://lod.work.go.kr/task/대분류/_20","knwg_tchn_attd":[
                {"knwg_tchn_attd":"http://lod.work.go.kr/task/지식/객체지향_언어_지식","knwg_tchn_attd_label":"객체지향 언어 지식"},
                {"knwg_tchn_attd":"http://lod.work.go.kr/task/기술/REST_API_활용_기술","knwg_tchn_attd_label":"REST(REpresentational State Transfer) API 활용 기술"},
                {"knwg_tchn_attd":"http://lod.work.go.kr/task/태도/열린_태도","knwg_tchn_attd_label":"Java를 좋아하는 태도"}]},
              "인공지능 서비스 환경 분석":{"job_lrcl_cd":"http://lod.work.go.kr/task/대분류/_20","knwg_tchn_attd":[]},
              "회계 결산":{"job_lrcl_cd":"http://lod.work.go.kr/task/대분류/_02","knwg_tchn_attd":[
                {"knwg_tchn_attd":"http://lod.work.go.kr/task/기술/엑셀","knwg_tchn_attd_label":"Python 기반 회계 자동화"}]}
            }}""";

    @Test
    void 직무정보는_지식_기술만_모으고_태도는_버린다() throws ExternalApiException {
        List<DutyInfoCollector.Unit> units = DutyInfoCollector.parse(DUTY_JSON);

        assertEquals(3, units.size());
        DutyInfoCollector.Unit api = units.get(0);
        assertEquals("생체인식 API 설계", api.name());
        assertEquals(List.of("객체지향 언어 지식", "REST(REpresentational State Transfer) API 활용 기술"),
                api.knowledgeAndSkills());
    }

    @Test
    void 정보통신_대분류20만_IT_능력단위로_본다() throws ExternalApiException {
        List<DutyInfoCollector.Unit> units = DutyInfoCollector.parse(DUTY_JSON);

        assertEquals(List.of(true, true, false), units.stream().map(DutyInfoCollector.Unit::isIt).toList());
    }

    @Test
    void 결과가_없으면_빈_목록() throws ExternalApiException {
        assertTrue(DutyInfoCollector.parse("{\"result\":{}}").isEmpty());
        assertTrue(DutyInfoCollector.parse("{}").isEmpty());
    }

    @Test
    void 깨진_JSON은_파싱_예외() {
        assertThrows(ExternalApiException.class, () -> DutyInfoCollector.parse("{\"result\":"));
    }

    // ---------------------------------------------------------------- NCS 문장 → SKILL

    private static NcsSkillExtractor extractor(String... names) {
        List<SkillDto> skills = new java.util.ArrayList<>();
        long id = 1;
        for (String name : names) {
            SkillDto s = new SkillDto();
            s.setId(id++);
            s.setSkillName(name);
            skills.add(s);
        }
        return new NcsSkillExtractor(skills);
    }

    private static Set<String> extractNames(NcsSkillExtractor ex, String... sentences) {
        return ex.extract(List.of(sentences)).stream().map(ex::nameOf).collect(Collectors.toSet());
    }

    @Test
    void 설명형_문장에서_기술명을_찾는다() {
        NcsSkillExtractor ex = extractor("REST API", "Linux", "SQL", "Java");

        assertEquals(Set.of("REST API"), extractNames(ex, "REST(REpresentational State Transfer) API 활용 기술"));
        assertEquals(Set.of("Linux"), extractNames(ex, "리눅스 운영체제 관리 기술"));
        assertEquals(Set.of("SQL"), extractNames(ex, "SQL(Structured Query Language) 작성 기술"));
        assertEquals(Set.of(), extractNames(ex, "객체지향 언어 지식"));
    }

    @Test
    void 다른_단어의_일부는_매칭하지_않는다() {
        NcsSkillExtractor ex = extractor("Java", "JavaScript", "SQL", "MySQL");

        assertEquals(Set.of("JavaScript"), extractNames(ex, "JavaScript 프로그래밍"));
        assertEquals(Set.of("MySQL"), extractNames(ex, "MySQL 데이터베이스 운영"));
        assertEquals(Set.of("JavaScript"), extractNames(ex, "자바스크립트 문법 지식"));
        assertEquals(Set.of("Java"), extractNames(ex, "자바 언어 지식"));
    }

    @Test
    void 한_글자_기술명은_별칭으로만_찾는다() {
        NcsSkillExtractor ex = extractor("C", "R", "C++");

        assertEquals(Set.of(), extractNames(ex, "C 등급 인증 지식", "R&D 기획"));
        assertEquals(Set.of("C"), extractNames(ex, "C언어 프로그래밍"));
        assertEquals(Set.of("C++"), extractNames(ex, "C++ 객체지향 프로그래밍"));
    }

    // ---------------------------------------------------------------- 고용24 오류 응답 감지

    @Test
    void HTTP_200이어도_본문_오류는_실패로_본다() {
        assertThrows(ExternalApiException.class, () ->
                Work24Client.checkApiError("213L01", "<?xml version='1.0'?><GO24><error>신청하신 OpenApi 서비스가 존재하지 않습니다</error></GO24>"));
        assertThrows(ExternalApiException.class, () ->
                Work24Client.checkApiError("215L01", "{\"message_cd\":\"E001\",\"message\":\"인증키 오류\"}"));
        assertThrows(ExternalApiException.class, () -> Work24Client.checkApiError("212L01", "  "));
        assertDoesNotThrow(() -> Work24Client.checkApiError("212L01", "<jobsList><total>0</total></jobsList>"));
    }
}
