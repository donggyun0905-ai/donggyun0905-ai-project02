package com.specodyssey.util;

import jakarta.el.ELContext;
import jakarta.el.ExpressionFactory;
import jakarta.el.PropertyNotWritableException;
import jakarta.el.StandardELContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tomcat 10.1(getX만)·11(x만) 어느 쪽 규칙으로 만든 record든 JSP에서 같은 값이 읽히는지. */
class RecordElResolverTest {

    // 구성요소만 있는 record — 10.1은 getName()이 없어 못 읽던 모양
    record Plain(String name, int count, List<String> tags) {
    }

    // 계산 값을 getX()/isX()로만 둔 record — 11은 x()만 찾아 못 읽던 모양
    record Derived(int total) {
        public String getLabel() {
            return total + "개";
        }

        public boolean isEmpty() {
            return total == 0;
        }

        public String isNotBoolean() { // is로 시작해도 boolean이 아니면 속성으로 보지 않는다
            return "x";
        }
    }

    public static class Bean {
        public String getName() {
            return "bean";
        }
    }

    private final RecordElResolver resolver = new RecordElResolver();

    @Test
    void 구성요소는_x로_읽는다() {
        Plain plain = new Plain("자바", 3, List.of("a"));
        assertEquals("자바", read(plain, "name"));
        assertEquals(3, read(plain, "count"));
        assertEquals(List.of("a"), read(plain, "tags"));
    }

    @Test
    void 계산_값은_getX와_isX로_읽는다() {
        Derived derived = new Derived(0);
        assertEquals("0개", read(derived, "label"));
        assertEquals(true, read(derived, "empty"));
        assertEquals(0, read(derived, "total"));
    }

    @Test
    void 없는_속성과_record가_아닌_값은_다음_해석기에_넘긴다() {
        ELContext context = context();
        assertNull(resolver.getValue(context, new Plain("a", 1, List.of()), "missing"));
        assertFalse(context.isPropertyResolved());

        assertNull(resolver.getValue(context, new Bean(), "name"));
        assertFalse(context.isPropertyResolved());

        assertNull(resolver.getValue(context, new Derived(1), "notBoolean"));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    void record는_읽기_전용이다() {
        ELContext context = context();
        assertTrue(resolver.isReadOnly(context, new Plain("a", 1, List.of()), "name"));
        assertThrows(PropertyNotWritableException.class,
                () -> resolver.setValue(context(), new Plain("a", 1, List.of()), "name", "b"));
    }

    private Object read(Object base, String property) {
        ELContext context = context();
        Object value = resolver.getValue(context, base, property);
        assertTrue(context.isPropertyResolved(), property + "를 해석하지 못했다");
        return value;
    }

    private static ELContext context() {
        return new StandardELContext(ExpressionFactory.newInstance());
    }
}
