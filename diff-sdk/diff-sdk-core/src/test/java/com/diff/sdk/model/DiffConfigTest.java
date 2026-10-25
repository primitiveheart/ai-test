package com.diff.sdk.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffConfigTest {

    @Test
    void defaultsAreNoOp() {
        DiffConfig config = DiffConfig.defaults();

        assertTrue(config.getIgnorePaths().isEmpty());
        assertEquals(Integer.MAX_VALUE, config.getMaxDepth());
        assertTrue(config.isLenientNumberComparison());
        assertFalse(config.isIgnored("a.b"));
        assertSame(config, DiffConfig.defaults(), "defaults 返回同一个不可变实例");
    }

    @Test
    void builderAppliesOptions() {
        DiffConfig config = DiffConfig.builder()
                .ignorePaths("meta.**", "*.timestamp")
                .maxDepth(5)
                .lenientNumbers(false)
                .build();

        assertEquals(Arrays.asList("meta.**", "*.timestamp"), config.getIgnorePaths());
        assertEquals(5, config.getMaxDepth());
        assertFalse(config.isLenientNumberComparison());
    }

    @Test
    void ignorePathsDeduplicatedAndTrimmed() {
        DiffConfig config = DiffConfig.builder()
                .ignorePaths("meta", " meta ", "meta")
                .build();

        assertEquals(Collections.singletonList("meta"), config.getIgnorePaths());
    }

    @Test
    void nonPositiveMaxDepthMeansUnlimited() {
        assertEquals(Integer.MAX_VALUE, DiffConfig.builder().maxDepth(0).build().getMaxDepth());
        assertEquals(Integer.MAX_VALUE, DiffConfig.builder().maxDepth(-3).build().getMaxDepth());
        assertEquals(1, DiffConfig.builder().maxDepth(1).build().getMaxDepth());
    }

    @Test
    void rejectsBlankIgnorePaths() {
        assertThrows(IllegalArgumentException.class,
                () -> DiffConfig.builder().ignorePaths((String) null));
        assertThrows(IllegalArgumentException.class,
                () -> DiffConfig.builder().ignorePaths("  "));
        assertThrows(NullPointerException.class,
                () -> DiffConfig.builder().ignorePaths((java.util.Collection<String>) null));
    }

    @Test
    void isIgnoredCoversSubtree() {
        DiffConfig config = DiffConfig.builder().ignorePaths("user.**", "*.pointer").build();

        assertTrue(config.isIgnored("user"));
        assertTrue(config.isIgnored("user.name"));
        assertTrue(config.isIgnored("user.roles.0.name"));
        assertTrue(config.isIgnored("meta.pointer"));

        assertFalse(config.isIgnored("order.name"));
        assertFalse(config.isIgnored(""), "根节点始终参与对比");
        assertFalse(config.isIgnored("userX"), "通配符 'user.**' 不模糊匹配 'userX'");
    }

    @Test
    void configIsImmutableAfterBuild() {
        DiffConfig config = DiffConfig.builder().ignorePaths("meta").build();

        assertThrows(UnsupportedOperationException.class, () -> config.getIgnorePaths().add("other"));
    }

    @Test
    void equalityIsValueBased() {
        DiffConfig a = DiffConfig.builder().ignorePaths("meta").maxDepth(3).build();
        DiffConfig b = DiffConfig.builder().ignorePaths("meta").maxDepth(3).build();
        DiffConfig c = DiffConfig.builder().ignorePaths("meta").maxDepth(4).build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, DiffConfig.defaults());
    }
}
