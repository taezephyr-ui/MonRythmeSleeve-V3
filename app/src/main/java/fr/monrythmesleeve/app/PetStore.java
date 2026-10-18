package fr.monrythmesleeve.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;

public final class PetStore {
    public static final String[] NAMES={"Petit dragon","Petit cochon","Petite loutre","Petit chat","Panda roux","Petit axolotl","Petit lapin","Petit hérisson"};
    public static final String[] ACCESSORIES={"Nœud","Couronne","Lunettes","Écharpe","Fleur","Chapeau"};
    public static final String[] DECORS={"Jardin fleuri","Nuit étoilée","Bord de mer","Forêt enchantée"};
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("sleeve_companion",Context.MODE_PRIVATE);}
    public static boolean adopted(Context c){return prefs(c).contains("adopted");}
    public static void adopt(Context c,int species,String name){
        SharedPreferences.Editor e=prefs(c).edit().putInt("species",species).putString("name",name.trim().isEmpty()?NAMES[species]:name.trim());
        if(!adopted(c))e.putString("adopted",LocalDate.now().toString()).putLong("adopted_at",System.currentTimeMillis());
        e.putLong("care",System.currentTimeMillis()).apply();
    }
    public static String name(Context c){return prefs(c).getString("name","Mon compagnon");}
    public static LocalDate adoption(Context c){return LocalDate.parse(prefs(c).getString("adopted",LocalDate.now().toString()));}
    public static int accessories(Context c){return Math.max(prefs(c).getInt("claimed_a",0),PetRules.accessories(adoption(c),LocalDate.now()));}
    public static int specials(Context c){return Math.max(prefs(c).getInt("claimed_s",0),PetRules.specials(adoption(c),LocalDate.now()));}
    public static int pending(Context c){return accessories(c)-prefs(c).getInt("claimed_a",0)+specials(c)-prefs(c).getInt("claimed_s",0);}
    public static void claim(Context c){prefs(c).edit().putInt("claimed_a",accessories(c)).putInt("claimed_s",specials(c)).putLong("care",System.currentTimeMillis()).apply();}
    public static String accessory(int i){return ACCESSORIES[i%ACCESSORIES.length]+" · édition "+(i/ACCESSORIES.length+1);}
    public static String decor(int i){return DECORS[i%DECORS.length]+" · édition "+(i/DECORS.length+1);}
    public static void care(Context c){prefs(c).edit().putLong("care",System.currentTimeMillis()).apply();}
    public static final class State {
        public boolean drink=true,active=false,sulk=false;
        public int meal=-1;
        public String message="Un petit moment ensemble ?";
    }
    public static State state(Context c,long now){
        State out=new State();LocalDate d=ScheduleStore.day(c);
        long adopted=prefs(c).getLong("adopted_at",now),care=prefs(c).getLong("care",now);
        long windowStart=0;
        for(int offset=-1;offset<=1;offset++){
            LocalDate day=d.plusDays(offset);int[] times=ScheduleStore.timesFor(c,day);
            for(int i=0;i<ScheduleStore.count(c);i++){
                HistoryStore.Entry r=ScheduleStore.record(c,day,i);
                long start=r!=null&&r.started>0?r.started:ScheduleStore.at(day,times[i]);
                boolean active=r!=null&&r.started>0&&r.finished==0;
                long end=r!=null&&r.finished>0?r.finished:start+30*60_000L;
                if(PetRules.blocked(now,start,end,active))out.drink=false;
                if(end+30*60_000L<=now)windowStart=Math.max(windowStart,end+30*60_000L);
                if(active){out.active=true;out.message="On mange tranquillement ensemble.";}
                if(offset==0&&(r==null||(r.started==0&&r.finished==0))&&start<=now&&out.meal<0)out.meal=i;
                if(offset==0&&start>=adopted&&start+15*60_000L<now&&care<start&&r==null)out.sulk=true;
            }
        }
        int hour=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).getHour();
        if(out.drink&&hour>=7&&hour<23&&windowStart>=adopted&&now-windowStart>60*60_000L&&prefs(c).getLong("drink",0)<windowStart&&care<windowStart)out.sulk=true;
        if(!out.active)out.message=out.sulk?"Je boude un tout petit peu… un câlin ?":out.drink?"On peut boire ensemble 💧":"Je me repose pendant la pause boissons.";
        if(hour<7&&out.meal<0&&!out.active){out.sulk=false;out.message="Chut… je fais un petit somme.";}
        return out;
    }
}
