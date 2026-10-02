package cn.screenqa.lite;
import org.junit.Test;
import static org.junit.Assert.*;
public class ThemeDefaultTest {
    @Test public void freshAndUnknownThemeUseIvory(){
        assertEquals("ivory_light",ThemePalette.DEFAULT.id);
        assertSame(ThemePalette.IVORY_LIGHT,ThemePalette.ALL[0]);
        assertSame(ThemePalette.IVORY_LIGHT,ThemePalette.fromStored("unknown"));
        assertEquals("ivory_light",ThemePalette.defaultMigration(null,false,false));
    }
    @Test public void previousDefaultsMigrateWithoutOverridingOtherThemes(){
        assertEquals("ivory_light",ThemePalette.defaultMigration("overlay_green",false,true));
        assertEquals("ivory_light",ThemePalette.defaultMigration("black_gold",false,false));
        assertEquals("black_gold",ThemePalette.defaultMigration("black_gold",false,true));
        assertEquals("crimson_night",ThemePalette.defaultMigration("crimson_night",false,true));
    }
    @Test public void recordedExplicitChoicesSurviveDefaultChange(){
        assertEquals("overlay_green",ThemePalette.defaultMigration("overlay_green",true,true));
        assertEquals("black_gold",ThemePalette.defaultMigration("black_gold",true,false));
    }
}
