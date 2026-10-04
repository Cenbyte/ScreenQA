package cn.screenqa.lite;

/** Uses the user's width/height rule without changing portrait phone layouts. */
final class PadLayout {
    private PadLayout(){}
    static boolean usesPadLayout(int width,int height){return width>height&&height>0;}
}
