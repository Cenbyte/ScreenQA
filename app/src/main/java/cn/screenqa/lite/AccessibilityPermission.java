package cn.screenqa.lite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/** User-controlled system authorization; never writes secure settings. */
final class AccessibilityPermission {
    static boolean enabled(Activity activity) {
        String services=android.provider.Settings.Secure.getString(activity.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName expected=new ComponentName(activity,ScreenQaAccessibilityService.class);
        if(services!=null)for(String service:services.split(":"))
            if(expected.equals(ComponentName.unflattenFromString(service)))return true;
        return false;
    }
    static boolean open(Activity activity,int request) {
        ComponentName component=new ComponentName(activity,ScreenQaAccessibilityService.class);
        // Some ROMs support a service detail page; all others fall back to the public list action.
        if(Build.VERSION.SDK_INT>=29)try {
            activity.startActivityForResult(new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME,component),request);
            QaLog.event("ACCESSIBILITY_PERMISSION open=service_details");return true;
        } catch(android.content.ActivityNotFoundException|SecurityException ignored) { }
        try {
            Bundle args=new Bundle();args.putString(":settings:fragment_args_key",component.flattenToString());
            activity.startActivityForResult(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .putExtra(":settings:fragment_args_key",component.flattenToString())
                    .putExtra(":settings:show_fragment_args",args),request);
            QaLog.event("ACCESSIBILITY_PERMISSION open=service_list");return true;
        } catch(android.content.ActivityNotFoundException|SecurityException e) {
            QaLog.event("ACCESSIBILITY_PERMISSION failed="+e.getClass().getSimpleName());return false;
        }
    }
}
