package com.specodyssey.service.archive;

import com.specodyssey.dto.TechArticleAttachmentDto;
import com.specodyssey.service.archive.ArchiveContentCodec.Encoded;
import com.specodyssey.service.archive.ArchiveContentCodec.ImageLink;
import com.specodyssey.service.archive.ArchiveContentCodec.Segment;
import com.specodyssey.service.archive.ArchiveContentCodec.Upload;
import com.specodyssey.service.archive.ArchiveContentCodec.Youtube;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 본문 ↔ 첨부 위치 변환 단위테스트 (DB 없이 실행) */
class ArchiveContentCodecTest {

    private static final String YT = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    // ---------------------------------------------------------------- 저장 (encode)

    @Test
    void 편집기의_사진_자리를_첨부_번호로_바꾼다() {
        Encoded e = ArchiveContentCodec.encode("첫 문단\n[[upload:7]]\n둘째 문단\n[[upload:2]]", Set.of(2, 7));

        assertEquals("첫 문단\n[[att:0]]\n둘째 문단\n[[att:1]]", e.content());
        assertEquals(List.of(new Upload(7), new Upload(2)), e.items(), "본문에 놓인 순서대로");
    }

    @Test
    void 올라오지_않았거나_두_번_가리킨_사진_자리는_지운다() {
        Encoded e = ArchiveContentCodec.encode("가[[upload:9]]나[[upload:1]]다[[upload:1]]", Set.of(1));

        assertEquals("가나[[att:0]]다", e.content());
        assertEquals(List.of(new Upload(1)), e.items());
    }

    @Test
    void 유튜브와_이미지_링크는_자동으로_첨부로_옮긴다() {
        Encoded e = ArchiveContentCodec.encode(
                "영상 보세요\n" + YT + "\n그림: https://i.imgur.com/abc.PNG.\n참고 https://velog.io/@me/post", Set.of());

        assertEquals("영상 보세요\n[[att:0]]\n그림: [[att:1]].\n참고 https://velog.io/@me/post", e.content(),
                "일반 링크는 본문에 그대로, 문장 끝 마침표는 링크에서 뺀다");
        assertEquals(new Youtube(YT, "dQw4w9WgXcQ"), e.items().get(0));
        assertEquals(new ImageLink("https://i.imgur.com/abc.PNG"), e.items().get(1));
    }

    @Test
    void http_이미지_링크는_첨부로_만들지_않는다() {
        Encoded e = ArchiveContentCodec.encode("http://example.com/a.png", Set.of());
        assertTrue(e.items().isEmpty());
        assertEquals("http://example.com/a.png", e.content());
    }

    @Test
    void 링크와_사진은_글자_수에_세지_않는다() {
        Encoded e = ArchiveContentCodec.encode("가나다 " + YT + " https://velog.io/" + "a".repeat(500) + "\n[[upload:0]]", Set.of(0));
        // 남는 것: "가나다" 3자 + 링크 앞뒤 공백 2개 — 링크(500자 넘음)와 사진 줄(줄바꿈 포함)은 0
        assertEquals(5, e.countedLength());
    }

    @Test
    void 사진만_있는_줄은_줄바꿈까지_세지_않는다_편집기와_같은_숫자() {
        // 편집기는 글 칸 "A"와 "B"를 줄바꿈 하나로 이어 3자로 센다
        assertEquals(3, ArchiveContentCodec.encode("A\n[[upload:0]]\nB", Set.of(0)).countedLength());
        assertEquals(1, ArchiveContentCodec.encode("[[upload:0]]\nA", Set.of(0)).countedLength());
        assertEquals(1, ArchiveContentCodec.encode("A\n[[upload:0]]", Set.of(0)).countedLength());
    }

    @Test
    void 사용자가_직접_쓴_첨부_표시는_무력화한다() {
        Encoded e = ArchiveContentCodec.encode("남의 첨부 [[att:3]] 끌어오기", Set.of());
        assertFalse(e.content().contains("[[att:3]]"));
        assertTrue(e.items().isEmpty());
    }

    // ---------------------------------------------------------------- 화면 (decode)

    private static TechArticleAttachmentDto att(int order, String type, String url) {
        TechArticleAttachmentDto a = new TechArticleAttachmentDto();
        a.setSortOrder(order);
        a.setAttachmentType(type);
        a.setUrl(url);
        return a;
    }

    private static List<String> describe(List<Segment> segments) {
        List<String> out = new ArrayList<>();
        for (Segment s : segments) {
            out.add(s.isMedia() ? "MEDIA:" + s.attachment().getSortOrder()
                    : s.isCodeBlock() ? "CODE:" + s.codeLanguage() + ":" + s.code() : "TEXT:" + s.text());
        }
        return out;
    }

    @Test
    void 글과_사진을_놓인_순서대로_나눈다() {
        List<Segment> segs = ArchiveContentCodec.decode("첫 문단\n두 줄째\n[[att:0]]\n둘째 문단\n[[att:1]]",
                List.of(att(0, "IMAGE_UPLOAD", null), att(1, "YOUTUBE", YT)));

        assertEquals(List.of("TEXT:첫 문단\n두 줄째", "MEDIA:0", "TEXT:둘째 문단", "MEDIA:1"), describe(segs));
    }

    @Test
    void 글_중간의_링크는_글로_보여주고_그_줄_아래에_영상을_붙인다() {
        List<Segment> segs = ArchiveContentCodec.decode("이 영상 [[att:0]] 꼭 보세요\n끝",
                List.of(att(0, "YOUTUBE", YT)));

        assertEquals(List.of("TEXT:이 영상 " + YT + " 꼭 보세요", "MEDIA:0", "TEXT:끝"), describe(segs));
    }

    @Test
    void 본문에서_가리키지_않은_첨부는_맨_뒤에_붙인다() {
        List<Segment> segs = ArchiveContentCodec.decode("글만 있음", List.of(att(0, "IMAGE_UPLOAD", null)));
        assertEquals(List.of("TEXT:글만 있음", "MEDIA:0"), describe(segs));
    }

    @Test
    void 저장_후_다시_읽으면_순서가_그대로다() {
        Encoded e = ArchiveContentCodec.encode("A\n[[upload:0]]\nB " + YT + "\nC", Set.of(0));
        List<TechArticleAttachmentDto> rows = List.of(att(0, "IMAGE_UPLOAD", null), att(1, "YOUTUBE", YT));

        assertEquals(List.of("TEXT:A", "MEDIA:0", "TEXT:B " + YT, "MEDIA:1", "TEXT:C"),
                describe(ArchiveContentCodec.decode(e.content(), rows)));
    }

    // ---------------------------------------------------------------- 코드 블록

    @Test
    void 코드_블록_안의_링크와_표시는_건드리지_않는다() {
        String raw = "설명\n```java\nString u = \"" + YT + "\"; // [[att:0]] [[upload:1]]\n```\n끝";
        Encoded e = ArchiveContentCodec.encode(raw, Set.of(1));

        assertTrue(e.items().isEmpty(), "코드 속 유튜브 링크·업로드 표시는 첨부가 아니다");
        assertEquals(raw, e.content());
    }

    @Test
    void 코드_블록은_글자_수에_세지_않는다() {
        Encoded e = ArchiveContentCodec.encode("가나\n```python\n" + "x = 1\n".repeat(500) + "```\n다", Set.of());
        // 코드 3,000자는 0. 글 칸 "가나"와 "다"를 줄바꿈 하나로 이어 센다 (편집기와 같은 규칙)
        assertEquals(4, e.countedLength());
    }

    @Test
    void 언어는_정해진_목록으로_맞추고_모르는_언어는_텍스트로() {
        assertEquals("```javascript\na\n```", ArchiveContentCodec.encode("```JS\na\n```", Set.of()).content());
        assertEquals("```plaintext\na\n```", ArchiveContentCodec.encode("```brainfuck\na\n```", Set.of()).content());
        assertEquals("```cpp\na\n```", ArchiveContentCodec.encode("```c++\na\n```", Set.of()).content());
    }

    @Test
    void 코드에_백틱_세_개가_있으면_펜스를_더_길게_만든다() {
        String code = "echo '```'\n```";
        String stored = ArchiveContentCodec.fence("bash", code);
        assertTrue(stored.startsWith("````bash\n"));

        List<Segment> segs = ArchiveContentCodec.decode(stored, List.of());
        assertEquals(1, segs.size());
        assertEquals(code, segs.get(0).code(), "코드 속 ``` 줄이 블록을 닫지 않는다");
    }

    @Test
    void 닫히지_않은_펜스는_글로_둔다() {
        Encoded e = ArchiveContentCodec.encode("```java\n안 닫힘", Set.of());
        assertEquals(List.of("TEXT:```java\n안 닫힘"), describe(ArchiveContentCodec.decode(e.content(), List.of())));
    }

    @Test
    void 글_사진_코드가_놓인_순서대로_나온다() {
        Encoded e = ArchiveContentCodec.encode("A\n[[upload:0]]\n```sql\nSELECT 1;\n```\nB", Set.of(0));
        List<Segment> segs = ArchiveContentCodec.decode(e.content(), List.of(att(0, "IMAGE_UPLOAD", null)));

        assertEquals(List.of("TEXT:A", "MEDIA:0", "CODE:sql:SELECT 1;", "TEXT:B"), describe(segs));
        assertEquals("SQL", segs.get(2).getCodeLanguageLabel());
    }

    @Test
    void 목록_미리보기에는_첨부_표시가_보이지_않는다() {
        assertEquals("첫 문단 둘째 문단", ArchiveContentCodec.plainPreview("첫 문단\n[[att:0]]\n둘째 문단", 90));
        assertEquals("설명 [코드] 끝", ArchiveContentCodec.plainPreview("설명\n```java\nint a;\n```\n끝", 90));
        assertEquals("가나…", ArchiveContentCodec.plainPreview("가나다라", 2));
    }
}
