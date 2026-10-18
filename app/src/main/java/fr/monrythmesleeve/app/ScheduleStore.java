package fr.monrythmesleeve.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Calculs en minutes relatives au debut de la journee : 00h30 lendemain = minute 1470. */
public final class ScheduleStore {
    private ScheduleStore() { }
    public static final int MEAL_DURATION=30, DRINKS_BEFORE=30, DRINKS_AFTER=30;
    public static final String[] NAMES={"Petit-déjeuner","Collation 1","Déjeuner","Collation 2","Dîner","Collation 6"};
    private static final String PREFS="mon_rythme_sleeve_v2";
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static int gap(Context c){int n=prefs(c).getInt("gap",150);return n==165||n==180?n:150;}
    public static int count(Context c){return prefs(c).getBoolean("sixth",false)?6:5;}
    public static String name(int i){return NAMES[i];}
    public static String hhmm(int m){m=Math.floorMod(m,1440);return String.format(Locale.FRANCE,"%02d:%02d",m/60,m%60);}
    public static String display(int m){return hhmm(m)+(m>=1440?" (+1 jour)":"");}
    public static String displayTimestamp(long ms){return java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm",Locale.FRANCE));}
    public static LocalDate day(Context c){prepare(c);return LocalDate.parse(prefs(c).getString("plan_day",LocalDate.now().toString()));}
    /** Horaires civils, y compris les jours de changement d'heure ete/hiver. */
    public static long at(LocalDate d,int minutes){
        int days=Math.floorDiv(minutes,1440), m=Math.floorMod(minutes,1440);
        return LocalDateTime.of(d.plusDays(days),LocalTime.of(m/60,m%60)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    public static int minuteOf(LocalDate d,long time){
        ZonedDateTime t=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        long diff=ChronoUnit.DAYS.between(d,t.toLocalDate());
        return (int)(diff*1440+t.getHour()*60+t.getMinute());
    }
    /** Arrondit vers la minute suivante pour ne jamais raccourcir l'intervalle minimal. */
    public static int ceilMinuteOf(LocalDate d,long time){
        ZonedDateTime z=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        return minuteOf(d,time)+((z.getSecond()!=0||z.getNano()!=0)?1:0);
    }
    public static int[] defaultTimes(Context c){
        SharedPreferences p=prefs(c);int[] out=new int[6];
        for(int i=0;i<5;i++)out[i]=p.getInt("base_"+i,p.getInt("meal_"+i,450+i*180));
        out[5]=p.getInt("base_5",out[4]+MEAL_DURATION+gap(c));return out;
    }
    /** Conserve la collation nocturne sur la date du planning precedent,
     * au moins jusqu'a 4 h et plus tard si cette collation est programmee tard. */
    public static void prepare(Context c){
        SharedPreferences p=prefs(c);ZonedDateTime now=ZonedDateTime.now();LocalDate today=now.toLocalDate();
        String stored=p.getString("plan_day",null);LocalDate active=null;
        try{if(stored!=null)active=LocalDate.parse(stored);}catch(Exception ignored){}
        int snack=p.getInt("day_meal_5",defaultTimes(c)[5]);
        int cutoff=count(c)==6?Math.max(240,snack+MEAL_DURATION+60-1440):240;
        int clock=now.getHour()*60+now.getMinute();
        if(active!=null && (today.isBefore(active)||today.isAfter(active.plusDays(1))||(today.isAfter(active)&&clock>=cutoff)))active=null;
        if(active==null){
            active=(clock<cutoff && count(c)==6)?today.minusDays(1):today;
            int[] defaults=defaultTimes(c);SharedPreferences.Editor e=p.edit().putString("plan_day",active.toString());
            for(int i=0;i<6;i++)e.putInt("day_meal_"+i,defaults[i]);e.apply();
        }
    }
    public static int[] times(Context c){prepare(c);SharedPreferences p=prefs(c);int[] defaults=defaultTimes(c);int[] out=new int[6];for(int i=0;i<6;i++)out[i]=p.getInt("day_meal_"+i,defaults[i]);return out;}
    public static int[] timesFor(Context c,LocalDate d){
        if(d.equals(day(c)))return times(c);
        return defaultTimes(c);
    }
    public static boolean saveAnchor(Context c,int index,int minutes,int chosenGap){
        if(index<0||index>=count(c)||chosenGap<150||chosenGap>180||chosenGap%15!=0)return false;
        if(minutes<0||minutes>1800)return false;
        int step=chosenGap+MEAL_DURATION;
        int first=minutes-index*step;
        int dinnerEnd=first+4*step+MEAL_DURATION;
        if(first<DRINKS_BEFORE||dinnerEnd>1440)return false;
        SharedPreferences.Editor e=prefs(c).edit().putInt("gap",chosenGap);
        for(int i=0;i<6;i++){int m=first+i*step;e.putInt("base_"+i,m).putInt("day_meal_"+i,m);}
        e.apply();return true;
    }
    public static void setSixth(Context c,boolean yes){
        SharedPreferences.Editor e=prefs(c).edit().putBoolean("sixth",yes);
        if(yes){int[] t=times(c);int sixth=t[4]+30+gap(c);e.putInt("day_meal_5",sixth).putInt("base_5",sixth);}e.apply();
    }
    public static boolean started(Context c,int index){try(HistoryStore db=new HistoryStore(c)){HistoryStore.Entry r=db.get(day(c).toString(),index);return r!=null&&r.started>0;}}
    public static boolean completed(Context c,int index){try(HistoryStore db=new HistoryStore(c)){HistoryStore.Entry r=db.get(day(c).toString(),index);return r!=null&&r.finished>0;}}
    public static HistoryStore.Entry record(Context c,LocalDate day,int index){try(HistoryStore db=new HistoryStore(c)){return db.get(day.toString(),index);}}
    public static boolean begin(Context c,int index,long actual){
        if(index<0||index>=count(c)||started(c,index))return false;
        LocalDate d=day(c);int[] t=times(c);
        try(HistoryStore db=new HistoryStore(c)){db.start(d.toString(),index,name(index),at(d,t[index]),actual);}
        int start=minuteOf(d,actual),step=gap(c)+MEAL_DURATION;
        int next=ceilMinuteOf(d,actual+MEAL_DURATION*60_000L)+gap(c);
        SharedPreferences.Editor e=prefs(c).edit().putInt("day_meal_"+index,start);
        for(int i=index+1;i<count(c);i++)e.putInt("day_meal_"+i,next+(i-index-1)*step);
        e.apply();return true;
    }
    /** Correct only the start; never erase recorded finishes or later real meals. */
    public static String correctStart(Context c,LocalDate d,int index,long actual){
        if(index<0||index>=count(c)||!d.equals(day(c)))return "Le planning a changé. Rouvre le repas.";
        HistoryStore.Entry r=record(c,d,index);
        if(actual>System.currentTimeMillis()||actual<at(d,0)||actual>=at(d,2880))return "Choisis une heure passée de cette journée ou du lendemain.";
        if(r!=null&&r.finished>0&&actual>r.finished)return "Le début doit précéder la fin enregistrée.";
        for(int j=0;j<count(c);j++){
            if(j==index)continue;
            HistoryStore.Entry other=record(c,d,j);
            if(other==null)continue;
            if(j<index&&((other.finished>actual)||(other.started>actual)||(other.started>0&&other.finished==0)))return "Vérifie la fin de la prise précédente.";
            if(j>index&&((other.started>0&&other.started<actual)||(other.finished>0&&other.finished<actual)))return "Le début doit précéder les prises suivantes.";
        }
        int[] t=times(c);
        try(HistoryStore db=new HistoryStore(c)){db.start(d.toString(),index,name(index),r==null?at(d,t[index]):r.planned,actual);}
        SharedPreferences.Editor edit=prefs(c).edit().putInt("day_meal_"+index,minuteOf(d,actual));
        long end=r!=null&&r.finished>0?r.finished:actual+MEAL_DURATION*60_000L;
        int next=ceilMinuteOf(d,end+gap(c)*60_000L);
        for(int j=index+1;j<count(c);j++){
            HistoryStore.Entry other=record(c,d,j);
            if(other!=null&&(other.started>0||other.finished>0))break;
            edit.putInt("day_meal_"+j,next);next+=MEAL_DURATION+gap(c);
        }
        edit.apply();return null;
    }
    public static boolean finish(Context c,int index,long actual){
        if(index<0||index>=count(c)||completed(c,index))return false;
        LocalDate d=day(c);int[] t=times(c);HistoryStore.Entry r=record(c,d,index);
        if(r!=null&&r.started>actual)return false;
        try(HistoryStore db=new HistoryStore(c)){db.finish(d.toString(),index,name(index),at(d,t[index]),actual);}
        int next=ceilMinuteOf(d,actual+gap(c)*60_000L),step=gap(c)+MEAL_DURATION;
        SharedPreferences.Editor e=prefs(c).edit();
        for(int i=index+1;i<count(c);i++)e.putInt("day_meal_"+i,next+(i-index-1)*step);
        e.apply();return true;
    }
    public static long expectedEnd(Context c,LocalDate d,int i){
        HistoryStore.Entry r=record(c,d,i);
        if(r!=null&&r.finished>0)return r.finished;
        if(r!=null&&r.started>0)return r.started+30*60_000L;
        return at(d,timesFor(c,d)[i]+MEAL_DURATION);
    }
    public static boolean alertsEnabled(Context c){return prefs(c).getBoolean("alerts",true);}
    public static boolean eventEnabled(Context c,int kind){return prefs(c).getBoolean("event_"+kind,true);}
}

