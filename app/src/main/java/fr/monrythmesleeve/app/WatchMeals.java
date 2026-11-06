package fr.monrythmesleeve.app;

import android.content.Context;
import com.google.android.gms.wearable.*;
import java.time.LocalDate;

public class WatchMeals extends WearableListenerService {
    static final String STATE="/sleeve/state", MEAL="/sleeve/meal/", REQUEST="/sleeve/request";
    static void put(Context c, PutDataMapRequest r){
        Wearable.getDataClient(c).putDataItem(r.asPutDataRequest().setUrgent());
    }
    static void fill(Context c,DataMap m){
        LocalDate d=ScheduleStore.day(c); int count=ScheduleStore.count(c), next=count; long start=0;
        for(int i=0;i<count;i++){
            HistoryStore.Entry e=ScheduleStore.record(c,d,i);
            if(e!=null && e.started>0 && e.finished==0){next=i;start=e.started;break;}
            if(next==count && (e==null || e.finished==0))next=i;
        }
        m.putString("day",d.toString());m.putInt("index",next);m.putInt("count",count);m.putLong("start",start);
        m.putString("label",next<count?ScheduleStore.name(next):"Journée terminée");
        int cutoff=ScheduleStore.count(c)==6?Math.max(240,ScheduleStore.times(c)[5]+ScheduleStore.MEAL_DURATION+60-1440):240;
        m.putLong("expires",ScheduleStore.at(d.plusDays(1),cutoff));
        m.putLong("stamp",System.currentTimeMillis());
    }
    static void publish(Context c){PutDataMapRequest r=PutDataMapRequest.create(STATE);fill(c,r.getDataMap());put(c,r);}
    @Override public void onDataChanged(DataEventBuffer events){
        for(DataEvent e:events){if(e.getType()!=DataEvent.TYPE_CHANGED)continue;
            String p=e.getDataItem().getUri().getPath();
            if(REQUEST.equals(p)){publish(this);continue;}
            if(p==null||!p.startsWith(MEAL))continue;
            DataMap m=DataMapItem.fromDataItem(e.getDataItem()).getDataMap();
            String id=p.substring(MEAL.length());if(!id.matches("[a-f0-9-]{36}"))continue;
            String error=apply(this,m);
            if(error.isEmpty())AlarmScheduler.rescheduleAll(this);
            PutDataMapRequest ack=PutDataMapRequest.create("/sleeve/ack/"+id);
            DataMap a=ack.getDataMap();fill(this,a);a.putLong("endSeen",m.getLong("end"));a.putString("error",error);
            put(this,ack);publish(this);
        }
    }
    static synchronized String apply(Context c,DataMap m){
        LocalDate d;try{d=LocalDate.parse(m.getString("day"));}catch(RuntimeException ex){return "Date inconnue";}
        int i=m.getInt("index",-1);long start=m.getLong("start"), end=m.getLong("end");
        if(i<0||i>=6||start<=0||start>System.currentTimeMillis()||end<0||(end!=0&&(end<start||end>System.currentTimeMillis())))return "Heure à vérifier";
        HistoryStore.Entry e=ScheduleStore.record(c,d,i);
        if(e==null || e.started==0){
            if(e!=null&&e.finished>0)return "Prise déjà terminée";
            if(!d.equals(ScheduleStore.day(c)))return "Planning changé";
            String err=ScheduleStore.correctStart(c,d,i,start);if(err!=null)return err;
        }else if(e.started!=start)return "Début différent sur le téléphone";
        if(end>0){e=ScheduleStore.record(c,d,i);if(e.finished!=0)return e.finished==end?"":"Fin différente sur le téléphone";
            String err=ScheduleStore.correctFinish(c,d,i,end);if(err!=null)return err;
        }
        return "";
    }
}