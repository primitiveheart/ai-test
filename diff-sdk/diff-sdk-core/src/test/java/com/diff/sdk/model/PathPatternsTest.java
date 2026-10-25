package com.diff.sdk.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathPatternsTest {

    @Test
    void matchesExactPath() {
        assertTrue(PathPatterns.matches("a.b", "a.b"));
        assertTrue(PathPatterns.matches("items.0.name", "items.0.name"));
        assertFalse(PathPatterns.matches("a.b", "a.c"));
        assertFalse(PathPatterns.matches("a.b", "a.b.c"), "精确模式不匹配子树");
    }

    @Test
    void matchesQuestionMarkWithinSegment() {
        assertTrue(PathPatterns.matches("item?.name", "item0.name"));
        assertTrue(PathPatterns.matches("it.m?me", "it.mXme"));
        assertFalse(PathPatterns.matches("item?.name", "item01.name"));
        assertFalse(PathPatterns.matches("item?.name", "item.name"), "? 要求恰好一个字符");
    }

    @Test
    void matchesStarWithinSegment() {
        assertTrue(PathPatterns.matches("a.*.c", "a.b.c"));
        assertTrue(PathPatterns.matches("a.*.c", "a..c".replace("..", ".x.")), "a.x.c 命中");
        assertFalse(PathPatterns.matches("a.*.c", "a.b.d.c"), "* 不跨 '.'");
        assertFalse(PathPatterns.matches("a.*.c", "a.b.c.d"));
    }

    @Test
    void matchesDoubleStarAcrossSegments() {
        assertTrue(PathPatterns.matches("a.**.name", "a.0.x.name"));
        assertTrue(PathPatterns.matches("a.**.name", "a.name"), "** 可匹配零个段");
        assertTrue(PathPatterns.matches("**", "a.b.c"));
        assertTrue(PathPatterns.matches("**.version", "version"));
        assertFalse(PathPatterns.matches("a.**.name", "a.0.x.nickname"));
    }

    @Test
    void matchesAnyCollection() {
        List<String> patterns = Arrays.asList("meta.**", "*.timestamp", "list.*.internalId");

        assertTrue(PathPatterns.matchesAny(patterns, "meta.trace.id"));
        assertTrue(PathPatterns.matchesAny(patterns, "user.timestamp"));
        assertTrue(PathPatterns.matchesAny(patterns, "list.3.internalId"));

        assertFalse(PathPatterns.matchesAny(patterns, "user.name"));
        assertFalse(PathPatterns.matchesAny(patterns, "list.3.extra.internalId"));
        assertFalse(PathPatterns.matchesAny(null, "meta"));
        assertFalse(PathPatterns.matchesAny(patterns, null));
    }

    @Test
    void splitsPathIgnoringEmptySegments() {
        assertEquals(Arrays.asList("a", "0", "b"), PathPatterns.split("a.0.b"));
        assertEquals(Arrays.asList("a", "b"), PathPatterns.split("a..b"));
        assertTrue(PathPatterns.split("").isEmpty());
    }

    @Test
    void nullPatternNeverMatches() {
        assertFalse(PathPatterns.matches(null, "a"));
        assertFalse(PathPatterns.matches("a", null));
    }
}
