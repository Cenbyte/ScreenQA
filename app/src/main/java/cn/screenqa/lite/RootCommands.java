package cn.screenqa.lite;

/** Fixed commands only; no arbitrary shell input is accepted from UI or model responses. */
final class RootCommands {
    private RootCommands(){}
    static String enableAccessibility(int user,String component,String shortComponent) {
        if(user<0||!validComponent(component)||!validComponent(shortComponent))
            throw new IllegalArgumentException("Invalid accessibility component");
        String settings="/system/bin/settings --user "+user;
        // Read/merge/write/verify in the same authorized shell, preserving the existing list.
        return "set -f\ncomponent="+quote(component)+"\nshort_component="+quote(shortComponent)+"\n"+
                "before=$("+settings+" get secure enabled_accessibility_services) || exit 20\n"+
                "[ \"$before\" != 'null' ] || before=''\n"+
                "case \":$before:\" in\n"+
                "  *\":$component:\"*|*\":$short_component:\"*) merged=$before ;;\n"+
                "  *) if [ -n \"$before\" ]; then merged=\"$before:$component\"; else merged=$component; fi ;;\n"+
                "esac\n"+
                settings+" put secure enabled_accessibility_services \"$merged\" || exit 21\n"+
                settings+" put secure accessibility_enabled 1 || exit 22\n"+
                "after=$("+settings+" get secure enabled_accessibility_services) || exit 23\n"+
                "case \":$after:\" in *\":$component:\"*|*\":$short_component:\"*) ;; *) exit 24 ;; esac\n"+
                "old_ifs=$IFS; IFS=:\n"+
                "for service in $before; do\n"+
                "  [ -n \"$service\" ] || continue\n"+
                "  case \":$after:\" in *\":$service:\"*) ;; *) exit 25 ;; esac\n"+
                "done\nIFS=$old_ifs\n"+
                "[ \"$("+settings+" get secure accessibility_enabled)\" = '1' ] || exit 26\ntrue";
    }
    static String tap(int x,int y,int width,int height) {
        if(width<=0||height<=0||x<0||y<0||x>=width||y>=height)
            throw new IllegalArgumentException("Tap outside current screen");
        // Existing capture and accessibility coordinates already describe the default display.
        return "/system/bin/input touchscreen tap "+x+" "+y;
    }
    static String swipe(int x,int y,int endX,int endY,int duration,int width,int height) {
        tap(x,y,width,height);tap(endX,endY,width,height);
        if(duration<40||duration>1000)throw new IllegalArgumentException("Invalid swipe duration");
        return "/system/bin/input touchscreen swipe "+x+" "+y+" "+endX+" "+endY+" "+duration;
    }
    private static boolean validComponent(String value) {
        return value!=null&&value.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+");
    }
    private static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
}
