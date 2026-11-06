package fr.monrythmesleeve.watch;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.widget.*;
import com.google.android.gms.wearable.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class MealActivity extends Activity implements DataClient.OnDataChangedListener {
    private SharedPreferences p;
    private DataClient client;
    private TextView title,info;
    private Button begin,finish;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String time(long t){return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        p=getSharedPreferences("meals",MODE_PRIVATE);client=Wearable.getDataClient(this);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout box=new LinearLayout(this);
        box.setOrientation(1);box.setGravity(Gravity.CENTER);box.setPadding(dp(24),dp(16),dp(24),dp(16));scroll.addView(box);
        title=new TextView(this);title.setGravity(Gravity.CENTER);title.setTextSize(16);box.addView(title);
        begin=button(box,"Je commence",0xffc9a5ed);finish=button(box,"J’ai terminé",0xff95d4c2);
        info=new TextView(this);info.setTextSize(12);info.setGravity(Gravity.CENTER);box.addView(info);
        begin.setOnClickListener(v->tap(false));finish.setOnClickListener(v->tap(true));setContentView(scroll);render();
    }
    private Button button(LinearLayout box,String text,int color){Button b=new Button(this);b.setText(text);b.setAllCaps(false);
        b.setTextColor(0xff201629);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        box.addView(b,new LinearLayout.LayoutParams(-1,dp(46)));return b;}
    private String id(){return p.getString("id","");}
    private boolean pending(){return !id().isEmpty();}
    private boolean available(){String day=p.getString("day","");return !day.isEmpty()&&p.getInt("index",6)<p.getInt("count",0)&&System.currentTimeMillis()<p.getLong("expires",0);}
    private void render(){long start=pending()?p.getLong("localStart",0):p.getLong("start",0),end=pending()?p.getLong("localEnd",0):0;
        title.setText(pending()?p.getString("localLabel","Prise"):p.getString("label","Mon Rythme Sleeve"));
        begin.setEnabled(!pending()&&available()&&start==0);finish.setEnabled(start>0&&end==0&&(pending()||available()));
        String warning=p.getString("warning","");
        info.setText(!warning.isEmpty()?warning:pending()?(end>0?"Fin "+time(end):"Début "+time(start))+" · en attente":start>0?"En cours depuis "+time(start):p.getString("day","").isEmpty()?"En attente du planning du téléphone":!available()&&p.getInt("index",6)<p.getInt("count",0)?"Reconnecte le téléphone pour le planning du jour":p.getString("confirmation","Prête"));
    }
    private void tap(boolean stop){long now=System.currentTimeMillis();SharedPreferences.Editor e=p.edit();
        if(!pending()){if(!available())return;
            long start=p.getLong("start",0);if(stop!=(start>0))return;
            e.putString("id",UUID.randomUUID().toString()).putString("localDay",p.getString("day","")).putInt("localIndex",p.getInt("index",0))
             .putString("localLabel",p.getString("label","Prise")).putLong("localStart",stop?start:now);
        }else if(!stop||p.getLong("localEnd",0)>0)return;
        if(!e.putLong("localEnd",stop?now:0).remove("warning").commit())return;
        render();send();
    }
    private void send(){if(!pending())return;PutDataMapRequest r=PutDataMapRequest.create("/sleeve/meal/"+id());DataMap m=r.getDataMap();
        m.putString("day",p.getString("localDay",""));m.putInt("index",p.getInt("localIndex",-1));m.putLong("start",p.getLong("localStart",0));m.putLong("end",p.getLong("localEnd",0));
        m.putLong("retry",System.currentTimeMillis());client.putDataItem(r.asPutDataRequest().setUrgent());}
    @Override protected void onResume(){super.onResume();client.addListener(this).addOnSuccessListener(v->client.getDataItems().addOnSuccessListener(items->{try{for(DataItem i:items)read(i);}finally{items.release();}}));
        PutDataMapRequest r=PutDataMapRequest.create("/sleeve/request");r.getDataMap().putLong("time",System.currentTimeMillis());client.putDataItem(r.asPutDataRequest().setUrgent());send();render();}
    @Override protected void onPause(){client.removeListener(this);super.onPause();}
    @Override public void onDataChanged(DataEventBuffer events){for(DataEvent e:events)if(e.getType()==DataEvent.TYPE_CHANGED)read(e.getDataItem());}
    private void read(DataItem item){String path=item.getUri().getPath();if(path==null)return;
        boolean ack=path.equals("/sleeve/ack/"+id())&&pending();if(!ack&&!path.equals("/sleeve/state"))return;
        DataMap m=DataMapItem.fromDataItem(item).getDataMap();SharedPreferences.Editor e=p.edit();
        if(m.getLong("stamp")>=p.getLong("stamp",0))e.putString("day",m.getString("day")).putInt("index",m.getInt("index")).putInt("count",m.getInt("count"))
            .putString("label",m.getString("label")).putLong("start",m.getLong("start")).putLong("expires",m.getLong("expires")).putLong("stamp",m.getLong("stamp"));
        boolean done=ack&&m.getLong("endSeen")==p.getLong("localEnd",0);
        if(done){String error=m.getString("error","");long end=p.getLong("localEnd",0), at=end>0?end:p.getLong("localStart",0);
            e.putString("warning",error.isEmpty()?"":time(at)+" · "+error+". Vérifie sur le téléphone.").putString("confirmation",(end>0?"Fin ":"Début ")+time(at)+" synchronisé").remove("id");}
        String oldId=id();if(!e.commit())return;render();
        if(done){client.deleteDataItems(item.getUri());client.deleteDataItems(android.net.Uri.parse("wear://*/sleeve/meal/"+oldId),DataClient.FILTER_LITERAL);}
    }
}