package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class InlineMarkdownTest {
    @Test public void boldItalicAndStrikeUseUtf16Offsets() {
        InlineMarkdown.Result result = InlineMarkdown.parse("😀 **重要** *强调* ~~过期~~");
        assertEquals("😀 重要 强调 过期", result.text);
        assertRange(result, 0, 3, 5, InlineMarkdown.BOLD);
        assertRange(result, 1, 6, 8, InlineMarkdown.ITALIC);
        assertRange(result, 2, 9, 11, InlineMarkdown.STRIKE);
    }

    @Test public void nestedStylesRetainBothRanges() {
        InlineMarkdown.Result result = InlineMarkdown.parse("**粗体和 *斜体* 一起**");
        assertEquals("粗体和 斜体 一起", result.text);
        assertRange(result, 0, 4, 6, InlineMarkdown.ITALIC);
        assertRange(result, 1, 0, 9, InlineMarkdown.BOLD);
        assertEquals("a b c", InlineMarkdown.parse("*a **b** c*").text);
    }

    @Test public void tripleStarsProduceBoldItalic() {
        InlineMarkdown.Result result = InlineMarkdown.parse("***强调***");
        assertEquals("强调", result.text);
        assertRange(result, 0, 0, 2, InlineMarkdown.BOLD | InlineMarkdown.ITALIC);
    }

    @Test public void repeatedAndAdjacentSegmentsAreIndependent() {
        InlineMarkdown.Result result = InlineMarkdown.parse("**甲**~~乙~~ *丙* **丁**");
        assertEquals("甲乙 丙 丁", result.text);
        assertEquals(4, result.spans.size());
    }

    @Test public void incompleteAndWhitespaceMarkersStayLiteral() {
        for (String text : new String[]{"**未闭合", "* 未加粗 *", "****", "~~ ~~", "**a\nb**", "a_b_c", "2 * 3 = 6", "~文字~"})
            assertUnchanged(text);
    }

    @Test public void escapedDelimitersAndUnclosedCodeStayLiteral() {
        for (String text : new String[]{"\\**原文**", "\\*原文*", "\\~~原文~~", "\\`原文\\`", "\\```原文\\```", "`**未闭合代码**", "```java\n**未闭合代码**", "```\n```"})
            assertUnchanged(text);
        assertEquals("**代码** 文字", InlineMarkdown.parse("`**代码**` **文字**").text);
        assertEquals("\\\\粗体", InlineMarkdown.parse("\\\\**粗体**").text);
    }

    @Test public void codeDoesNotInterpretEmphasisOrBackslash() {
        InlineMarkdown.Result result = InlineMarkdown.parse("`**a** *b* ~~c~~ \\d`");
        assertEquals("**a** *b* ~~c~~ \\d", result.text);
        assertEquals(1, result.spans.size());
        assertRange(result, 0, 0, result.text.length(), InlineMarkdown.CODE);
    }

    @Test public void variableBacktickRunsAllowLiteralTicksInside() {
        InlineMarkdown.Result result = InlineMarkdown.parse("``a `b` c``");
        assertEquals("a `b` c", result.text);
        assertRange(result, 0, 0, 7, InlineMarkdown.CODE);
        assertEquals("abc", InlineMarkdown.parse("```abc```").text);
    }

    @Test public void codeAndEmphasisHaveCorrectEmojiOffsets() {
        InlineMarkdown.Result result = InlineMarkdown.parse("😀 `x` **粗**");
        assertEquals("😀 x 粗", result.text);
        assertRange(result, 0, 3, 4, InlineMarkdown.CODE);
        assertRange(result, 1, 5, 6, InlineMarkdown.BOLD);
        result = InlineMarkdown.parse("**a `*b*` c**");
        assertEquals("a *b* c", result.text);
        assertEquals(2, result.spans.size());
        assertRange(result, 0, 2, 5, InlineMarkdown.CODE);
        assertRange(result, 1, 0, 7, InlineMarkdown.BOLD);
    }

    @Test public void fencedCodeStripsInfoAndKeepsBodyWhitespace() {
        String body = "  a();\n\tb();\n";
        InlineMarkdown.Result result = InlineMarkdown.parse("前\n```java\n" + body + "```\n**后**");
        assertEquals("前\n" + body + "后", result.text);
        assertRange(result, 0, 2, 2 + body.length(), InlineMarkdown.CODE);
        assertRange(result, 1, 2 + body.length(), 3 + body.length(), InlineMarkdown.BOLD);
    }

    @Test public void indentedFencesAndCrLfArePreservedCorrectly() {
        InlineMarkdown.Result result = InlineMarkdown.parse("  ```java\r\n  a\r\n ``` \t\r\n后");
        assertEquals("  a\r\n后", result.text);
        assertRange(result, 0, 0, 5, InlineMarkdown.CODE);
        assertEquals("x\ry\r", InlineMarkdown.parse("```\rx\ry\r```\r").text);
    }

    @Test public void closingFenceMustOccupyItsOwnLine() {
        String body = "first ``` kept\n```no\n";
        InlineMarkdown.Result result = InlineMarkdown.parse("```\n" + body + "```\n");
        assertEquals(body, result.text);
        assertEquals(1, result.spans.size());
    }

    @Test public void longerClosingFenceWorksAndShorterRunStaysInCode() {
        String body = "`a` **b**\n```\n";
        InlineMarkdown.Result result = InlineMarkdown.parse("````text\n" + body + "`````\n");
        assertEquals(body, result.text);
        assertRange(result, 0, 0, body.length(), InlineMarkdown.CODE);
    }

    @Test public void adjacentCodeAndStrikeRemainIndependent() {
        InlineMarkdown.Result result = InlineMarkdown.parse("`a`~~b~~`c`");
        assertEquals("abc", result.text);
        assertRange(result, 0, 0, 1, InlineMarkdown.CODE);
        assertRange(result, 1, 1, 2, InlineMarkdown.STRIKE);
        assertRange(result, 2, 2, 3, InlineMarkdown.CODE);
    }

    @Test public void inlineCodeKeepsWhitespaceWithoutOpeningMultilineEmphasis() {
        InlineMarkdown.Result result = InlineMarkdown.parse("**a `b\nc` d**");
        assertEquals("**a b\nc d**", result.text);
        assertEquals(1, result.spans.size());
        assertRange(result, 0, 4, 7, InlineMarkdown.CODE);
        assertEquals(" a\n b ", InlineMarkdown.parse("`` a\n b ``").text);
    }

    @Test public void codeSpanLimitFallsBackToEntireSource() {
        String source = "`a` ".repeat(InlineMarkdown.MAX_SPANS + 1);
        InlineMarkdown.Result result = InlineMarkdown.parse(source);
        assertTrue(result.limited);
        assertEquals(source, result.text);
        assertTrue(result.removed.isEmpty());
    }

    @Test public void unsupportedSyntaxIsNotInterpreted() {
        assertUnchanged("# 标题\n- 列表\n[链接](https://example.com)\n![图片](image.png)\n<b>HTML</b>");
    }

    @Test public void lengthLimitKeepsEntireSource() {
        String source = "**粗体**" + "a".repeat(InlineMarkdown.MAX_LENGTH);
        InlineMarkdown.Result result = InlineMarkdown.parse(source);
        assertTrue(result.limited);
        assertEquals(source, result.text);
        assertTrue(result.spans.isEmpty());
    }

    @Test public void spanLimitDoesNotLeaveAPartlyStrippedMessage() {
        String source = "**a** ".repeat(InlineMarkdown.MAX_SPANS + 1);
        InlineMarkdown.Result result = InlineMarkdown.parse(source);
        assertTrue(result.limited);
        assertEquals(source, result.text);
        assertTrue(result.removed.isEmpty());
    }

    @Test public void adversarialUnpairedRunsHaveBoundedWork() {
        String source = "*a ".repeat(1300);
        InlineMarkdown.Result result = InlineMarkdown.parse(source);
        assertTrue(result.limited);
        assertEquals(source, result.text);
    }

    @Test public void emptyAndPlainTextStayUnchanged() {
        assertUnchanged("");
        assertUnchanged("明天见 😀 [图片]");
    }

    @Test public void randomizedOffsetsAndDelimiterRemovalStayConsistent() {
        Random random = new Random(20261012);
        String alphabet = "ab *~`\\\n中文😀";
        for (int trial = 0; trial < 2000; trial++) {
            StringBuilder source = new StringBuilder();
            for (int i = random.nextInt(150); i > 0; i--) source.append(alphabet.charAt(random.nextInt(alphabet.length())));
            InlineMarkdown.Result result = InlineMarkdown.parse(source.toString());
            StringBuilder deleted = new StringBuilder(source);
            int previousEnd = 0;
            for (InlineMarkdown.Range range : result.removed) {
                assertTrue(range.start >= previousEnd);
                assertTrue(range.end > range.start && range.end <= source.length());
                previousEnd = range.end;
            }
            for (int i = result.removed.size() - 1; i >= 0; i--) {
                InlineMarkdown.Range range = result.removed.get(i);
                deleted.delete(range.start, range.end);
            }
            assertEquals(result.text, deleted.toString());
            for (InlineMarkdown.Range range : result.spans) {
                assertTrue(range.start >= 0);
                assertTrue(range.end > range.start && range.end <= result.text.length());
            }
        }
    }

    private static void assertUnchanged(String source) {
        InlineMarkdown.Result result = InlineMarkdown.parse(source);
        assertEquals(source, result.text);
        assertTrue(result.spans.isEmpty());
    }

    private static void assertRange(InlineMarkdown.Result result, int index, int start, int end, int style) {
        InlineMarkdown.Range range = result.spans.get(index);
        assertEquals(start, range.start);
        assertEquals(end, range.end);
        assertEquals(style, range.style);
    }
}
