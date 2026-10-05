package cn.screenqa.lite;

/** Immutable policy captured for one request. Continuous UI progress never reaches the API. */
final class ReasoningStrategy {
    static final String[] NAMES={"极速","快速","均衡","谨慎","深度"};
    final int reasoningLevel;
    ReasoningStrategy(int level){reasoningLevel=clamp(level);}
    static int clamp(int level){return Math.max(1,Math.min(5,level));}
    static float clampProgress(float progress){return Math.max(1f,Math.min(5f,progress));}
    static float mirror(float progress){return 6f-clampProgress(progress);}
    static int snap(float progress){return clamp(Math.round(progress));}
    boolean thinking(){return reasoningLevel>=4;}
    String effort(){return reasoningLevel==4?"high":"max";}
    String name(){return NAMES[reasoningLevel-1];}
    String prompt(){
        String analysis=switch(reasoningLevel){
            case 1,4 -> "尽可能快速作答，根据题目直接得出答案，不做交叉验证，不主动复核，不做扩展分析。";
            case 2,5 -> "对同一道题在内部独立求解两遍。第一遍得出候选答案；第二遍重新从原题条件出发求解，不沿用第一遍的结论或推导，尽量避免相互影响。最后只给一个最终答案；若两遍冲突，只检查关键分歧，不展开反复推导。";
            default -> "先在内部求解一遍，第二遍专门认真纠错：逐项核对题干关键条件、否定词、限定词、概念范围、选项差异和计算条件，检查遗漏与错误并修正。仅做一轮纠错，不无限复核。";
        };
        return analysis+"思考仅在内部进行，不输出思考过程。选择题仅输出选项字母，判断题仅给正确/错误。填空题按空位顺序返回每个空实际要填的短答案，不重复题干。简答题给可直接提交的精炼正文；简述宜短，分析论述可稍完整，并遵守题目字数限制。不要输出答案前缀、Markdown、解释、理由或解题步骤。条件缺失或图形不可读时明确说明，勿猜测。";
    }
    int tokenBudget(int answerLimit){return answerLimit+switch(reasoningLevel){case 4->8192;case 5->16384;default->0;};}
    /** Effort is documented for these official models; never assume custom gateways support it. */
    static boolean supportsEffort(String endpoint,String model){
        try{
            java.net.URI uri=java.net.URI.create(endpoint);
            return "https".equalsIgnoreCase(uri.getScheme())&&"api.deepseek.com".equalsIgnoreCase(uri.getHost())
                    &&(uri.getPort()==-1||uri.getPort()==443)&&uri.getUserInfo()==null
                    &&("deepseek-flash".equals(model)||"deepseek-v4-flash".equals(model)||"deepseek-v4-pro".equals(model));
        }catch(Exception e){return false;}
    }
}
