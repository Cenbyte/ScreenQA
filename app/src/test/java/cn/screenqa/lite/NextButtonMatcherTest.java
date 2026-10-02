package cn.screenqa.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class NextButtonMatcherTest {
    @Test public void acceptsExplicitNextLabels() {
        assertEquals(3,NextButtonMatcher.confidence("下一题 →",false));
        assertEquals(3,NextButtonMatcher.confidence("下一页",true));
        assertEquals(2,NextButtonMatcher.confidence("Next ›",true));
        assertEquals(0,NextButtonMatcher.confidence("Continue",false));
    }
    @Test public void rejectsTerminalAndAmbiguousActions() {
        assertEquals(0,NextButtonMatcher.confidence("提交答案",true));
        assertEquals(0,NextButtonMatcher.confidence("交卷",true));
        assertEquals(0,NextButtonMatcher.confidence("查看成绩 →",true));
        assertEquals(0,NextButtonMatcher.confidence("返回",true));
        assertEquals(0,NextButtonMatcher.confidence("继续并提交",true));
    }
}
