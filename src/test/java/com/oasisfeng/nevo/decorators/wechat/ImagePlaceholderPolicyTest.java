package com.oasisfeng.nevo.decorators.wechat;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ImagePlaceholderPolicyTest {
    @Test public void onlyTheDisplayedImageLosesItsPlaceholder() {
        assertEquals(2, ImagePlaceholderPolicy.target(Arrays.asList(
                row("[图片]", 9001, true), row("Synthetic text", 9002, true),
                row("[图片]", 9003, true)), 9003));
    }
    @Test public void appendingTextDoesNotMoveTheImageTarget() {
        assertEquals(0, ImagePlaceholderPolicy.target(Arrays.asList(
                row("[图片]", 9001, true), row("Synthetic later text", 9002, true)), 9001));
    }
    @Test public void unknownIdentityRequiresOneIncomingPlaceholder() {
        assertEquals(1, ImagePlaceholderPolicy.target(Arrays.asList(
                row("[图片]", 0, false), row("图片", 0, true)), 0));
        assertEquals(-1, ImagePlaceholderPolicy.target(Arrays.asList(
                row("[图片]", 0, true), row("[图片]", 0, true)), 0));
    }
    @Test public void missingOrConflictingExactIdentityDoesNotHideAnotherMessage() {
        assertEquals(-1, ImagePlaceholderPolicy.target(Arrays.asList(row("[图片]", 9001, true)), 9002));
        assertEquals(-1, ImagePlaceholderPolicy.target(Arrays.asList(
                row("[图片]", 9001, true), row("[图片]", 9001, true)), 9001));
    }
    @Test public void ordinaryTextAndSelfMessagesRemainText() {
        assertEquals(-1, ImagePlaceholderPolicy.target(Arrays.asList(row("Synthetic [图片] text", 9001, true)), 9001));
        assertEquals(-1, ImagePlaceholderPolicy.target(Arrays.asList(row("[图片]", 9001, false)), 9001));
        assertEquals(-1, ImagePlaceholderPolicy.target(Arrays.asList(row(null, 9001, true)), 9001));
    }
    private static ImagePlaceholderPolicy.Message row(String text, long serverId, boolean incoming) {
        return new ImagePlaceholderPolicy.Message(text, serverId, incoming);
    }
}
