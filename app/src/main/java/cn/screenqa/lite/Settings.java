package cn.screenqa.lite;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.net.URI;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Settings {
    private static final String ALIAS = "screenqa_api_key_v1";
    static final String ENDPOINT = "https://api.deepseek.com/chat/completions";
    static final String MODEL = "deepseek-flash";
    final SharedPreferences prefs;
    Settings(Context context) { prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    Settings(SharedPreferences preferences) { prefs = preferences; }
    boolean autoSelect() {return prefs.getBoolean("auto_select",false);}
    boolean autoNext() {return prefs.getBoolean("auto_next",false);}
    boolean nextOverlay(){return prefs.getBoolean("next_overlay",false);}
    void setNextOverlay(boolean value){prefs.edit().putBoolean("next_overlay",value).apply();}
    void setAutoNext(boolean value) {prefs.edit().putBoolean("auto_next",value).apply();}
    void setAutoSelect(boolean value) {prefs.edit().putBoolean("auto_select",value).apply();}
    boolean rootEnabled(){return BuildConfig.ROOT_SUPPORTED&&prefs.getBoolean("root_enabled",false);}
    void setRootEnabled(boolean enabled){prefs.edit().putBoolean("root_enabled",enabled).apply();}
    boolean rootAutoAccessibility(){return rootEnabled()&&prefs.getBoolean("root_auto_accessibility",true);}
    void setRootAutoAccessibility(boolean enabled){prefs.edit().putBoolean("root_auto_accessibility",enabled).apply();}
    boolean rootAnswerTap(){return rootEnabled()&&prefs.getBoolean("root_answer_tap",true);}
    void setRootAnswerTap(boolean enabled){prefs.edit().putBoolean("root_answer_tap",enabled).apply();}
    TouchPriority touchPriority(){return TouchPriority.fromStored(prefs.getInt("touch_priority",0));}
    void setTouchPriority(TouchPriority priority){prefs.edit().putInt("touch_priority",priority.ordinal()).apply();}
    boolean autoExecute(String type) {
        return switch(type) {
            case "choice" -> prefs.getBoolean("auto_choice",true);
            case "true_false" -> prefs.getBoolean("auto_true_false",true);
            case "fill_blank" -> prefs.getBoolean("auto_fill_blank",true);
            case "short_answer" -> prefs.getBoolean("auto_short_answer",false);
            default -> false;
        };
    }
    void setAutoExecute(String type,boolean enabled) {
        String key=switch(type) {
            case "choice" -> "auto_choice";
            case "true_false" -> "auto_true_false";
            case "fill_blank" -> "auto_fill_blank";
            case "short_answer" -> "auto_short_answer";
            default -> throw new IllegalArgumentException("Unknown question type");
        };
        prefs.edit().putBoolean(key,enabled).apply();
    }
    AutoAnswerStrategy strategy() {return AutoAnswerStrategy.fromStored(prefs.getInt("auto_strategy",0));}
    void setStrategy(AutoAnswerStrategy value) {prefs.edit().putInt("auto_strategy",value.ordinal()).apply();}
    String themeId(){return prefs.getString("theme_id",ThemePalette.DEFAULT.id);}
    ThemePalette theme(){return ThemePalette.fromStored(themeId());}
    void setThemeId(String id){prefs.edit().putString("theme_id",id).putBoolean("theme_user_selected",true).apply();}
    /**
     * Move the previous default to ivory once; retain other themes and recorded explicit selections.
     */
    void migrateThemeDefault(){
        if(prefs.getBoolean("theme_ivory_default_migrated",false))return;
        String stored=prefs.getString("theme_id",null);
        String target=ThemePalette.defaultMigration(stored,prefs.getBoolean("theme_user_selected",false),prefs.getBoolean("theme_default_migrated",false));
        prefs.edit().putBoolean("theme_default_migrated",true).putBoolean("theme_ivory_default_migrated",true).putString("theme_id",target).apply();
    }
    boolean reduceMotion(){return prefs.getBoolean("reduce_motion",false);}
    void setReduceMotion(boolean value){prefs.edit().putBoolean("reduce_motion",value).apply();}
    boolean glassDock(){return prefs.getBoolean("glass_dock",true);}
    void setGlassDock(boolean value){prefs.edit().putBoolean("glass_dock",value).apply();}
    /** Model ID sent to the API. Uses its own key so save() never wipes it during the legacy cleanup. */
    String modelId(){String value=prefs.getString("model_id","");return value==null||value.isEmpty()?MODEL:value;}
    void setModelId(String value){prefs.edit().putString("model_id",value).apply();}
    /** Complete chat/completions URL; stays on the official default until the user overrides it. */
    String endpoint(){String value=prefs.getString("endpoint_url","");return value==null||value.isEmpty()?ENDPOINT:value;}
    void setEndpoint(String value){prefs.edit().putString("endpoint_url",value).apply();}
    int reasoningLevel(){
        if(!prefs.contains("reasoning_level")){
            // An explicitly enabled legacy toggle migrates to cautious; all other installs start balanced.
            int level=prefs.getBoolean("thinking_enabled",false)?4:3;
            prefs.edit().putInt("reasoning_level",level).remove("thinking_enabled").apply();
        }
        return ReasoningStrategy.clamp(prefs.getInt("reasoning_level",3));
    }
    void setReasoningLevel(int value){prefs.edit().putInt("reasoning_level",ReasoningStrategy.clamp(value)).remove("thinking_enabled").apply();}
    /** Apply the requested balanced starting point once; later explicit choices remain saved. */
    void applyBalancedDefaultOnce(){
        if(prefs.getBoolean("reasoning_balanced_default_applied",false))return;
        prefs.edit().putInt("reasoning_level",3).remove("thinking_enabled")
                .putBoolean("reasoning_balanced_default_applied",true).apply();
    }
    String lastReadAnnouncementId(){return prefs.getString("lastReadAnnouncementId","");}
    void markAnnouncementRead(String id){prefs.edit().putString("lastReadAnnouncementId",id).apply();}
    boolean customEndpoint(){return !ENDPOINT.equals(endpoint());}
    static boolean isOfficialLegacyBase(String base) {
        try {
            URI uri=URI.create(base);
            return "https".equalsIgnoreCase(uri.getScheme()) && "api.deepseek.com".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo()==null && uri.getPort()==-1;
        } catch(Exception e) {return false;}
    }
    private SecretKey secret() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    String key() throws Exception {
        // An upgraded installation may contain a credential for another provider.
        // Keep the ciphertext stored, but never send it to DeepSeek as if it were a DeepSeek key.
        if(prefs.contains("base") && !isOfficialLegacyBase(prefs.getString("base","")))return "";
        String encrypted = prefs.getString("key", "");
        if (encrypted.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128,
                Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8);
    }
    void save(String key) throws Exception {
        if (key.trim().isEmpty()) throw new IllegalArgumentException("请填写 API Key");
        if (key.contains("\n") || key.contains("\r")) throw new IllegalArgumentException("API Key 不能包含换行");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret());
        String data = Base64.encodeToString(cipher.doFinal(key.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().remove("base").remove("model")
                .putString("key", data).putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit())
            throw new IllegalStateException("保存失败，请重试");
    }
}
