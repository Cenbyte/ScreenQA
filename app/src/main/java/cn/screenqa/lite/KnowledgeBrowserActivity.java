package cn.screenqa.lite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Embedded share browser; downloads never go to public Download or external apps. */
public final class KnowledgeBrowserActivity extends Activity {
    private WebView web;
    private TextView pageStatus;
    private ProgressBar pageProgress;
    private ThemePalette palette;
    private String passwordScript,fileLayoutScript;
    private KnowledgeDownloadSession session;
    private final ConcurrentHashMap<String,String> referers=new ConcurrentHashMap<>();
    private final List<WebView> popups=new ArrayList<>();
    private final List<Dialog> dialogs=new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        palette=new Settings(this).theme();
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        getWindow().getDecorView().setSystemUiVisibility(palette.dark?0:
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);root.setPadding(dp(12),0,dp(12),0);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
                view.setPadding(bars.left+dp(12),bars.top,bars.right+dp(12),bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft()+dp(12),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight()+dp(12),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout controls=new LinearLayout(this);
        root.addView(controls,new LinearLayout.LayoutParams(-1,-2));
        button(controls,"‹ 返回知识库",this::finish);
        button(controls,"重新打开",()->web.loadUrl(KnowledgeSourceConfig.URL));
        label(root,"在线获取知识库",17);
        label(root,"提取码 "+KnowledgeSourceConfig.EXTRACTION_CODE+" · 自动填写，亦可手动输入",12);
        pageStatus=label(root,"正在打开蓝奏云…",12);
        pageProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        root.addView(pageProgress,new LinearLayout.LayoutParams(-1,dp(3)));
        try(InputStream input=getAssets().open("knowledge_password.js")) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
            passwordScript=new String(bytes.toByteArray(),StandardCharsets.UTF_8)
                    .replace("__EXTRACTION_CODE__",JSONObject.quote(KnowledgeSourceConfig.EXTRACTION_CODE));
        } catch(Exception error) { pageStatus.setText("自动填写不可用，请手动输入提取码"); }
        try(InputStream input=getAssets().open("knowledge_file_layout.js")) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);fileLayoutScript=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        } catch(java.io.IOException error){android.util.Log.w("KnowledgeBrowser","文件页布局调整不可用",error);}
        web=new WebView(this);configure(web);
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(new KnowledgeProgressView(this,palette),new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        session=KnowledgeDownloadSession.get(this);

        if(state==null || web.restoreState(state)==null) web.loadUrl(KnowledgeSourceConfig.URL);
    }
    private int dp(int value) { return Ui.dp(this,value); }
    private TextView label(LinearLayout parent,String value,int size) {
        TextView text=new TextView(this);text.setText(value);text.setTextColor(palette.foreground);
        text.setTextSize(size);text.setPadding(0,dp(5),0,dp(5));parent.addView(text);return text;
    }
    private void button(LinearLayout parent,String title,Runnable action) {
        Button button=new Button(this);button.setText(title);button.setAllCaps(false);
        button.setTextColor(palette.accent);button.setTextSize(KnowledgeUi.BODY);button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.accentSoft));button.setElevation(0);parent.addView(button,parent.getOrientation()==LinearLayout.VERTICAL?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        button.setOnClickListener(v->action.run());
    }
    @SuppressLint("SetJavaScriptEnabled")
    private void configure(WebView view) {
        WebSettings settings=view.getSettings();
        settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,true);
        view.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView target,String url,Bitmap icon) {
                target.setTag(null);
                pageStatus.setText("正在加载网页…");pageProgress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView target,String url) {
                if(target.getTag()==null)pageStatus.setText("请选择知识包下载");
                injectPageEnhancements(target);CookieManager.getInstance().flush();
            }
            @Override public void onPageCommitVisible(WebView target,String url) { injectPageEnhancements(target); }
            @Override public boolean shouldOverrideUrlLoading(WebView target,WebResourceRequest request) {
                String url=request.getUrl().toString(),scheme=request.getUrl().getScheme();
                if(!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                    pageStatus.setText("此链接无法在内置浏览器下载，请选择网页下载按钮");return true;
                }
                String path=request.getUrl().getPath();
                if(request.hasGesture() && path!=null && path.toLowerCase(Locale.ROOT).matches(".*\\.(zip|7z|rar|tar|gz)$")) {
                    download(target,url,target.getSettings().getUserAgentString(),null,null);return true;
                }
                return false;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView target,WebResourceRequest request) {
                for(java.util.Map.Entry<String,String> header:request.getRequestHeaders().entrySet())
                    if("Referer".equalsIgnoreCase(header.getKey())) {
                        if(referers.size()>128)referers.clear();
                        referers.put(request.getUrl().toString(),header.getValue());
                    }
                return null;
            }
            @Override public void onReceivedError(WebView target,WebResourceRequest request,WebResourceError error) {
                if(request.isForMainFrame()) {
                    target.setTag("failed");
                    pageStatus.setText(getString(R.string.knowledge_page_error,error.getDescription()));
                }
            }
            @Override public void onReceivedHttpError(WebView target,WebResourceRequest request,WebResourceResponse response) {
                if(request.isForMainFrame()) {
                    target.setTag("failed");pageStatus.setText(getString(R.string.knowledge_page_http_error,response.getStatusCode()));
                }
            }
            @Override public void onReceivedSslError(WebView target,android.webkit.SslErrorHandler handler,android.net.http.SslError error) {
                handler.cancel();target.setTag("failed");pageStatus.setText("网页证书验证失败，请检查网络或稍后重新打开");
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView target,int progress) {
                pageProgress.setProgress(progress);pageProgress.setVisibility(progress==100?View.GONE:View.VISIBLE);
                if(progress>=60)injectPageEnhancements(target);
            }
            @Override public boolean onCreateWindow(WebView target,boolean dialog,boolean gesture,Message message) {
                if(!gesture)return false;
                WebView child=new WebView(KnowledgeBrowserActivity.this);configure(child);
                Dialog popup=new Dialog(KnowledgeBrowserActivity.this);popup.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                LinearLayout body=new LinearLayout(KnowledgeBrowserActivity.this);body.setOrientation(LinearLayout.VERTICAL);
                body.setBackgroundColor(palette.background);
                button(body,"关闭文件页面",popup::dismiss);
                body.addView(child,new LinearLayout.LayoutParams(-1,0,1));popup.setContentView(body);
                popups.add(child);dialogs.add(popup);
                popup.setOnDismissListener(d->{popups.remove(child);dialogs.remove(popup);child.stopLoading();child.destroy();});
                popup.show();popup.getWindow().setLayout(-1,-1);
                ((WebView.WebViewTransport)message.obj).setWebView(child);message.sendToTarget();return true;
            }
            @Override public void onCloseWindow(WebView window) {
                int index=popups.indexOf(window);if(index>=0)dialogs.get(index).dismiss();
            }
        });
        view.setDownloadListener((url,agent,disposition,mime,length)->download(view,url,agent,disposition,mime));
    }
    private void injectPageEnhancements(WebView target){
        injectPassword(target);
        if(fileLayoutScript!=null && KnowledgeSourceConfig.isPasswordPageOrigin(target.getUrl()))target.evaluateJavascript(fileLayoutScript,null);
    }
    private void injectPassword(WebView target) {
        if(passwordScript!=null && KnowledgeSourceConfig.isPasswordPageOrigin(target.getUrl()))
            target.evaluateJavascript(passwordScript,null);
    }
    private void download(WebView target,String url,String agent,String disposition,String mime) {
        String referer=referers.get(url);
        if(referer==null)referer=target.getUrl();
        if(!session.start(new KnowledgeDownloader.Request(url,agent,referer,disposition,mime)))
            Toast.makeText(this,"已有下载任务，请等待完成后再点击",Toast.LENGTH_SHORT).show();
        else {
            Toast.makeText(this,"App 已接管下载",Toast.LENGTH_SHORT).show();
            // File links may use target=_blank; return to the list so native progress stays visible.
            for(Dialog dialog:new ArrayList<>(dialogs))dialog.dismiss();
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out);web.saveState(out); }
    @Override protected void onResume() { super.onResume();if(web!=null)web.onResume(); }
    @Override protected void onPause() { if(web!=null)web.onPause();CookieManager.getInstance().flush();super.onPause(); }
    @Override public void onBackPressed() { if(web.canGoBack())web.goBack();else super.onBackPressed(); }
    @Override protected void onDestroy() {
        for(Dialog dialog:new ArrayList<>(dialogs))dialog.dismiss();
        if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();
    }
}
