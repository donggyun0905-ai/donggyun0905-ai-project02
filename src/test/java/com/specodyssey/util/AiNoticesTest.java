package com.specodyssey.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 요청 하나 동안 AI 대체 안내를 모으는 AiNotices (FR-111). DB 불필요. */
class AiNoticesTest {

    @BeforeEach
    void setUp() {
        AiNotices.clear();
    }

    @Test
    void 넣은_순서대로_꺼내고_꺼낸_뒤에는_비어_있다() {
        AiNotices.add("첫째");
        AiNotices.add("둘째");

        assertEquals(List.of("첫째", "둘째"), AiNotices.drain());
        assertTrue(AiNotices.drain().isEmpty());
    }

    @Test
    void 같은_문구는_한_번만_빈_문구는_무시한다() {
        AiNotices.add("같은 안내");
        AiNotices.add("같은 안내");
        AiNotices.add("  ");
        AiNotices.add(null);

        assertEquals(List.of("같은 안내"), AiNotices.drain());
    }

    @Test
    void 요청_밖에서_계속_쌓이지_않게_개수를_제한한다() {
        for (int i = 0; i < 20; i++) {
            AiNotices.add("안내 " + i);
        }

        assertEquals(AiNotices.MAX_NOTICES, AiNotices.drain().size());
    }

    @Test
    void 다른_스레드의_안내는_섞이지_않는다() throws Exception {
        AiNotices.add("이 스레드");
        AtomicReference<List<String>> other = new AtomicReference<>();
        Thread t = new Thread(() -> {
            AiNotices.add("다른 스레드");
            other.set(AiNotices.drain());
        });
        t.start();
        t.join();

        assertEquals(List.of("다른 스레드"), other.get());
        assertEquals(List.of("이 스레드"), AiNotices.drain());
    }
}
