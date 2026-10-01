package com.specodyssey.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHeadersFilterTest {

    @Test
    void 같은_호스트에서_온_요청은_통과한다() {
        assertTrue(SecurityHeadersFilter.hostMatches("http://localhost:8080/roadmap", "localhost:8080"));
        assertTrue(SecurityHeadersFilter.hostMatches("https://spec.example.com/roadmap", "spec.example.com"));
        assertTrue(SecurityHeadersFilter.hostMatches("HTTP://Spec.Example.com/x", "spec.example.com"));
    }

    @Test
    void 다른_호스트나_포트에서_온_요청은_막는다() {
        assertFalse(SecurityHeadersFilter.hostMatches("http://evil.example.com/form", "localhost:8080"));
        assertFalse(SecurityHeadersFilter.hostMatches("http://localhost:9999/form", "localhost:8080"));
        assertFalse(SecurityHeadersFilter.hostMatches("http://localhost.evil.com/", "localhost"));
    }

    @Test
    void 이상한_출처나_Host_헤더가_없으면_막는다() {
        assertFalse(SecurityHeadersFilter.hostMatches("not a url", "localhost"));
        assertFalse(SecurityHeadersFilter.hostMatches("file:///etc/passwd", "localhost"));
        assertFalse(SecurityHeadersFilter.hostMatches("http://localhost/", null));
    }
}
