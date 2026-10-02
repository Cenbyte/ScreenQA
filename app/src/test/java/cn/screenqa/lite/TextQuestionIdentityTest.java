package cn.screenqa.lite;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TextQuestionIdentityTest {
    private ScreenDocument doc(String... text) {
        List<ScreenDocument.Line> lines=new ArrayList<>();int y=200;
        for(String line:text){lines.add(new ScreenDocument.Line(line,20,y,1000,y+40));y+=55;}
        return new ScreenDocument(lines,1080,2400);
    }
    private TextQuestionIdentity identity(String... text) {
        ScreenDocument d=doc(text);List<Integer> ids=new ArrayList<>();
        for(int i=0;i<d.lines.size();i++)ids.add(i+1);
        return TextQuestionIdentity.from(d,ids);
    }
    @Test public void multilineOcrMatchesMergedWebViewWithoutTypeLabel() {
        assertTrue(identity("填空题","1. 植物通过叶片散失水分的过程", "叫作＿＿＿＿。").matches(
                "植物通过叶片散失水分的过程叫作____。请输入答案 下一题"));
    }
    @Test public void filledAnswerDoesNotBecomeANewQuestion() {
        TextQuestionIdentity id=identity("填空题","水由____和____两种元素组成。");
        assertTrue(id.matches("水由氢和氧两种元素组成。"));
        assertTrue(id.matches("水由____和____两种元素组成。氢 氧"));
    }
    @Test public void nextQuestionWithSamePrefixIsDifferent() {
        TextQuestionIdentity id=identity("人体通过____系统完成气体交换。");
        assertFalse(id.matches("人体通过消化系统完成食物消化。"));
        assertTrue(id.matches("人体通过呼吸系统完成气体交换。"));
    }
    @Test public void fingerprintLineNumbersDoNotPolluteMatching() {
        TextQuestionIdentity id=identity("简答题","简述植物蒸腾作用", "对水分运输的意义。");
        assertTrue(id.matches(doc("简述植物蒸腾作用对水分", "运输的意义。", "答案正文")));
    }
    @Test public void parenthesisAndFullwidthLatinNormalize() {
        assertTrue(identity("第 2 题 Ｈ２Ｏ中氢氧原子数比是（  ）。")
                .matches("H2O中氢氧原子数比是2:1。"));
    }
    @Test public void substantiveNumbersMustStillMatch() {
        assertFalse(identity("水在标准大气压下100摄氏度时发生____。")
                .matches("水在标准大气压下10摄氏度时发生凝固。"));
    }
    @Test public void absentStemAndUnrelatedPageNeverMatch() {
        TextQuestionIdentity id=identity("植物通过叶片散失水分的过程叫作____。");
        assertFalse(id.matches("下一题 请输入答案"));assertFalse(id.matches(""));
    }
    @Test public void typeLabelAloneIsNotAnIdentity() {
        assertFalse(identity("填空题","____").matches("填空题 植物 蒸腾作用"));
    }
    @Test public void manualAnswerEditingKeepsSameIdentityAndNewStemDoesNot() {
        TextQuestionIdentity id=identity("太阳系中最大的行星是____。");
        assertTrue(id.matches(doc("太阳系中最大的行星是____。","木星")));
        assertTrue(id.matches(doc("太阳系中最大的行星是____。","土星")));
        assertFalse(id.matches(doc("太阳系中最小的行星是____。","水星")));
    }
    @Test public void fragmentsCannotMatchInReverseOrder() {
        assertFalse(identity("比较植物____与动物____的细胞结构。")
                .matches("的细胞结构 动物 植物 比较植物"));
    }
    @Test public void numericalSignsAndDecimalsAreNotFormattingNoise() {
        assertFalse(identity("计算-12的绝对值为____。").matches("计算12的绝对值为12。"));
        assertFalse(identity("计算1.5加2的结果是____。").matches("计算15加2的结果是17。"));
        assertTrue(identity("计算−12的绝对值为____。").matches("计算-12的绝对值为12。"));
    }
}
