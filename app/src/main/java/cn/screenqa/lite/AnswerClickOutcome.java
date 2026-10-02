package cn.screenqa.lite;

/** Distinguishes a rejected action from a possibly injected action; prevents duplicate taps. */
final class AnswerClickOutcome {
    final boolean accepted,allowFallback,uncertain;
    private AnswerClickOutcome(boolean accepted,boolean allowFallback,boolean uncertain) {
        this.accepted=accepted;this.allowFallback=allowFallback;this.uncertain=uncertain;
    }
    static AnswerClickOutcome accessibility(boolean accepted){return new AnswerClickOutcome(accepted,!accepted,false);}
    static AnswerClickOutcome cancelled(){return new AnswerClickOutcome(false,false,false);}
    static AnswerClickOutcome uncertain(){return new AnswerClickOutcome(false,false,true);}
    static AnswerClickOutcome root(RootShell.Result result){
        return new AnswerClickOutcome(result.success(),
                !result.success()&&!result.mayHaveExecuted&&result.status!=RootShell.Status.CANCELLED,
                !result.success()&&result.mayHaveExecuted);
    }
}
