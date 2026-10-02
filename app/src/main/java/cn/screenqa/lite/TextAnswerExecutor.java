package cn.screenqa.lite;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

/** Acts on a freshly acquired editable node; the caller owns and releases the node. */
final class TextAnswerExecutor {
    private TextAnswerExecutor() {}
    static boolean editable(AccessibilityNodeInfo node) {
        if(node==null||!node.isEnabled()||!node.isVisibleToUser())return false;
        if(node.isEditable())return true;
        for(AccessibilityNodeInfo.AccessibilityAction action:node.getActionList())
            if(action.getId()==AccessibilityNodeInfo.ACTION_SET_TEXT)return true;
        return false;
    }
    static String write(Context context,AccessibilityNodeInfo node,String answer,java.util.function.BooleanSupplier allowed) {
        if(!allowed.getAsBoolean())return "";
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,answer);
        boolean accepted=allowed.getAsBoolean()&&node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
        if(accepted&&verified(node,answer))return "ACTION_SET_TEXT";
        if(!allowed.getAsBoolean()||!node.refresh()||!editable(node))return "";
        QaLog.event("TextInput fallback reason="+(accepted?"set_text_verification_failed":"set_text_rejected"));
        ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard==null)return "";
        try {
            // The target app may suppress clipboard reads. A safe restore is not always possible.
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if(!allowed.getAsBoolean()||!node.refresh()||!node.isFocused())return "";
            CharSequence existing=node.getText();
            if(existing!=null&&existing.length()>0) {
                Bundle selection=new Bundle();
                selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,0);
                selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,existing.length());
                if(!node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,selection))return "";
            }
            if(!allowed.getAsBoolean())return "";
            clipboard.setPrimaryClip(ClipData.newPlainText("大学生小帮手学习参考",answer));
            if(allowed.getAsBoolean()&&node.performAction(AccessibilityNodeInfo.ACTION_PASTE)&&verified(node,answer))return "CLIPBOARD_PASTE";
        } catch(Exception e) {QaLog.event("TextInput clipboard_exception="+e.getClass().getSimpleName());}
        return "";
    }
    private static boolean verified(AccessibilityNodeInfo node,String expected) {
        CharSequence actual=null;boolean okay=false;
        // WebView applies ACTION_SET_TEXT asynchronously; bounded worker-thread readback only.
        for(int attempt=0;attempt<4;attempt++) {
            if(attempt>0)android.os.SystemClock.sleep(60);
            if(!node.refresh())break;
            actual=node.getText();okay=actual!=null&&expected.equals(actual.toString().trim());
            if(okay)break;
        }
        QaLog.event("TextInput verification="+(okay?"success":"failed")+" actual_length="+
                (actual==null?0:actual.length())+" expected_length="+expected.length());
        return okay;
    }
}
