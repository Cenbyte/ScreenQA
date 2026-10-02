package cn.screenqa.lite;

/** Pure-Java state machine. Includes all options; never reuse an answer for a changed screen. */
final class QuestionTracker {
    private String candidate = "";
    private int stableFrames, generation, attempts;
    private boolean inFlight, answered;
    private long retryAt;
    static String normalize(String text) { return text == null ? "" : text.replaceAll("[\\s\\p{Z}]+", ""); }
    int observe(String text) {
        String next = normalize(text);
        if (!candidate.equals(next)) {
            candidate = next; stableFrames = 1; generation++;
            attempts = 0; inFlight = false; answered = false; retryAt = 0;
        } else { stableFrames++; }
        return generation;
    }
    boolean ready(long now) {
        return candidate.length() >= 6 && stableFrames >= 2 && !inFlight && !answered && attempts < 3 && now >= retryAt;
    }
    boolean waitingForStability(){return candidate.length()>=6&&stableFrames<2&&!inFlight&&!answered;}
    boolean retriesExhausted(){return attempts>=3&&!inFlight&&!answered;}
    long retryDelay(long now){return !inFlight&&!answered&&attempts>0&&attempts<3?Math.max(0,retryAt-now):0;}
    int begin() { inFlight = true; attempts++; return generation; }
    int attemptCount(){return attempts;}
    boolean isCurrent(int token) { return generation == token; }
    boolean complete(int token, boolean success, long now) {
        if (!isCurrent(token)) return false;
        inFlight = false; answered = success; retryAt = now + 10000;
        return true;
    }
    void reset() {
        candidate = ""; stableFrames = 0; generation++; attempts = 0;
        inFlight = false; answered = false; retryAt = 0;
    }
}
