package fr.monrythmesleeve.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.database.Cursor;
import org.json.JSONObject;
import java.time.LocalDate;

/** Local 2D companion. Data flows only from the real history into the game. */
final class PipouHouse {
    private final Activity host;
    WebView game;
    private boolean ready, foreground;
    PipouHouse(Activity host) {
        this.host=host;
        game=new WebView(host);
        WebSettings settings=game.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        game.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){return true;}
            @Override public void onPageFinished(WebView v,String url){
                if(!"file:///android_asset/pipou.html".equals(url))return;
                if(game!=v)return;ready=true;if(foreground)sync();
            }
        });

        game.loadUrl("file:///android_asset/pipou.html");
    }
    private void sync(){
        if(!ready)return;
        LocalDate today=LocalDate.now(); long now=System.currentTimeMillis();
        String snapshot="null";
        // Include last night's plan for the late snack. A finish-only entry never starts a meal.
        try(HistoryStore db=new HistoryStore(host); Cursor row=db.getReadableDatabase().rawQuery(
            "SELECT day,meal,title,started,finished FROM meals WHERE day IN (?,?) AND ((started>0 AND started<=?) OR (finished>0 AND finished<=?)) ORDER BY MAX(COALESCE(started,0),COALESCE(finished,0)) DESC,meal DESC LIMIT 1",
            new String[]{today.toString(),today.minusDays(1).toString(),Long.toString(now),Long.toString(now)})){
            if(row.moveToFirst()&&row.getLong(3)>0&&row.getLong(4)==0){
                JSONObject event=new JSONObject();
                event.put("key",row.getString(0)+":"+row.getInt(1));
                event.put("index",row.getInt(1));event.put("title",row.getString(2));
                snapshot=event.toString();
            }
        }catch(org.json.JSONException|android.database.SQLException unavailable){
            android.util.Log.w("Pipou","Historique indisponible",unavailable);
        }
        game.evaluateJavascript("if(window.pipouSleeve){pipouSleeve.visibility(true);pipouSleeve.receive("+snapshot+");}",null);
    }
    void resume(){foreground=true;if(game!=null){game.onResume();sync();}}
    void pause(){foreground=false;if(game!=null){if(ready)game.evaluateJavascript("window.pipouSleeve&&pipouSleeve.visibility(false)",null);game.onPause();}}
    void close(){if(game.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)game.getParent()).removeView(game);if(game!=null){game.destroy();game=null;}}
}
