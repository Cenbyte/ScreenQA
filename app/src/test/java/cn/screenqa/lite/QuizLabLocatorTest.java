package cn.screenqa.lite;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuizLabLocatorTest {
    private ScreenDocument page(String stem,String progress,String selected,int optionCount) {
        List<ScreenDocument.Line> lines=new ArrayList<>(Arrays.asList(
                new ScreenDocument.Line("答题实验室",0,128,1080,2339),
                new ScreenDocument.Line(stem,49,345,1031,2309),
                new ScreenDocument.Line(progress,49,548,259,637),
                new ScreenDocument.Line("#T002",855,805,976,868),
                new ScreenDocument.Line(stem,107,928,973,1133),
                new ScreenDocument.Line("请选择一个答案",105,1164,976,1209),
                new ScreenDocument.Line(stem,105,1243,976,1997)));
        String[] answers={"A，数据存储","B，页面样式","C，网络传输","D，用户登录"};
        for(int i=0;i<optionCount;i++)lines.add(new ScreenDocument.Line(answers[i],105,1243+194*i,976,1414+194*i));
        lines.add(new ScreenDocument.Line(selected,910,587,1031,674));
        return new ScreenDocument(lines,1080,2400);
    }
    @Test public void actualDomWithoutTypeAnchorUsesOnlyHeadingAndFourButtons(){
        ScreenDocument page=page("CSS 主要负责网页的什么？","2/10","已答 1 题",4);
        assertNull(LocalQuestionLocator.locate(page));
        LocalQuestionLocator.Candidate found=QuizLabLocator.locate("io.codex.quizlab",page);
        assertNotNull(found);assertEquals(5,found.all.size());assertEquals(1,found.stem.size());
        assertEquals("CSS 主要负责网页的什么？",found.document.text(found.stem));
        assertEquals("B，页面样式",AnswerTargetResolver.resolve("choice","B",found.document,found.all,found.stem).text);
    }
    @Test public void packageScopeAndMissingOptionsRejectUnreliablePages(){
        assertNull(QuizLabLocator.locate("other.app",page("CSS 主要负责网页的什么？","2/10","已答 1 题",4)));
        assertNull(QuizLabLocator.locate("io.codex.quizlab",page("CSS 主要负责网页的什么？","2/10","已答 1 题",3)));
        assertNull(QuizLabLocator.locate("io.codex.quizlab",page("CSS 主要负责网页的什么？","成绩","已答 1 题",4)));
    }
    @Test public void answeredCountDoesNotChangeIdentityButNewStemDoes(){
        LocalQuestionLocator.Candidate a=QuizLabLocator.locate("io.codex.quizlab",page("CSS 主要负责网页的什么？","2/10","已答 1 题",4));
        LocalQuestionLocator.Candidate b=QuizLabLocator.locate("io.codex.quizlab",page("CSS 主要负责网页的什么？","2/10","已答 2 题",4));
        LocalQuestionLocator.Candidate c=QuizLabLocator.locate("io.codex.quizlab",page("关系型数据库是什么？","3/10","已答 2 题",4));
        assertEquals(a.document.fingerprint(),b.document.fingerprint());
        assertNotEquals(a.document.fingerprint(),c.document.fingerprint());
    }
}
