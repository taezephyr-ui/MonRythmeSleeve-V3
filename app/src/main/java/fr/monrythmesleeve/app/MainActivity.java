package fr.monrythmesleeve.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Mon Rythme Sleeve V2 : Android natif, stockage local, sans connexion obligatoire. */
public class MainActivity extends Activity {
    private static final String[] TABS={"Accueil","Planning","Historique","Alertes","Paramètres","Compagnon"};
    private static final String[] ICONS={"⌂","▦","◷","♧","⚙","♡"};
    private static final String[] CARDS={"statut","prochaine","repas_0","repas_1","repas_2","repas_3","repas_4","repas_5","soutien","historique","fond"};
    private static final String[] CARD_NAMES={"Statut / hydratation","Prochaine étape","Petit-déjeuner","Collation 1","Déjeuner","Collation 2","Dîner","Collation 3","Message d'accueil","Historique","Fond général"};
    private static final String[] PATTERNS={"Uni","Dégradé","Fleurs","Feuilles","Bulles","Étoiles","Vagues"};
    private static final String[] COLOR_NAMES={"Rose poudré","Lavande","Bleu ciel","Vert sauge","Pêche","Crème","Prune","Turquoise","Nuit"};
    private static final int[] COLORS={0xFFF7DDEA,0xFFE5DDF7,0xFFDDEDFC,0xFFDDEEE4,0xFFFFE2D0,0xFFFFF1D6,0xFFB29BC9,0xFFCCEDEB,0xFF302B45};
    private static final int PICK_IMAGE=514;
    private int tab=0,anchor=4,selectedMinute=1170,chosenGap=150;
    private String chosenCard="statut";
    private LinearLayout root,page;
    private TextView countdown,statusTitle,statusDetail,nextLabel;
    private boolean dark;
    private int bg,ink,muted,accent,navBg;
    private int welcomeVariant;
    private List<NextEvent> cachedEvents;
    private boolean cachedMealActive;
    private long lastEventRefresh;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){if(tab==0)updateStatus();handler.postDelayed(this,1000);}};
    private PipouHouse house;
    private PetView petView;
    private TextView petMessage,petRewards;
    private Button petDrink,petEat;
    private LinearLayout rewardPanel;
    private String rewardStamp="";
    private final List<PetView> petSurfaces=new ArrayList<>();
    private long petRefresh=0,petActionUntil=0;
    private int petAction=0;

    @Override public void onCreate(Bundle state){super.onCreate(state);
        ScheduleStore.prepare(this);AlarmReceiver.ensureChannel(this);AlarmScheduler.rescheduleAll(this);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},200);
        welcomeVariant=ScheduleStore.prefs(this).getInt("opening_count",0);
        ScheduleStore.prefs(this).edit().putInt("opening_count",welcomeVariant+1).apply();
        if(PetStore.adopted(this)){try{CompanionIcon.apply(this,PetStore.prefs(this).getInt("species",0));}catch(RuntimeException ignored){}}
        show(0);
    }
    @Override protected void onResume(){super.onResume();if(house!=null)house.resume();handler.removeCallbacks(ticker);for(PetView view:petSurfaces)view.setForeground(true);handler.post(ticker);}
    @Override protected void onPause(){if(house!=null)house.pause();handler.removeCallbacks(ticker);for(PetView view:petSurfaces)view.setForeground(false);super.onPause();}
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] results){
        super.onRequestPermissionsResult(code,perms,results);
        if(code==200&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)AlarmScheduler.rescheduleAll(this);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_IMAGE&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            ScheduleStore.prefs(this).edit().putString("image_"+chosenCard,uri.toString()).apply();
            toast("Image enregistrée pour : "+cardTitle(chosenCard));show(4);
        }
    }
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private void theme(){
        String mode=ScheduleStore.prefs(this).getString("theme","AUTO");
        dark="SOMBRE".equals(mode)||("AUTO".equals(mode)&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        bg=dark?0xFF191520:0xFFFFFAFC;ink=dark?0xFFF7EDF9:0xFF493748;
        muted=dark?0xFFCCBED2:0xFF866F83;accent=ScheduleStore.prefs(this).getInt("accent",dark?0xFFCBA3F2:0xFFB56498);
        navBg=dark?0xFF25202E:Color.WHITE;
    }
    private GradientDrawable rounded(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void gap(LinearLayout l,int n){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private void add(LinearLayout parent,View view,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(bottom);parent.addView(view,lp);}
    private Button button(String label,boolean primary){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        int color=ScheduleStore.prefs(this).getInt(primary?"button_primary":"button_secondary",primary?accent:(dark?0xFF41344C:0xFFF2E6F3));
        b.setTextColor(Color.luminance(color)>0.35?0xFF211B2B:Color.WHITE);b.setBackground(rounded(color,15));
        b.setPadding(dp(11),dp(11),dp(11),dp(11));b.setMinHeight(dp(46));b.setElevation(0);return b;
    }
    private Bitmap loadPicture(String raw){
        if(raw==null||raw.isEmpty())return null;
        try{
            Uri uri=Uri.parse(raw);BitmapFactory.Options inspect=new BitmapFactory.Options();inspect.inJustDecodeBounds=true;
            try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,inspect);}
            int sample=1;while(inspect.outWidth/sample>1024||inspect.outHeight/sample>1024)sample*=2;
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample;
            try(InputStream in=getContentResolver().openInputStream(uri)){return BitmapFactory.decodeStream(in,null,opts);}
        }catch(Exception ignored){return null;}
    }
    private int defaultColor(String id){
        if(dark){if("statut".equals(id))return 0xFF2B453B;if("prochaine".equals(id))return 0xFF372C48;if(id.startsWith("repas"))return 0xFF2C2536;if("soutien".equals(id))return 0xFF342842;if("historique".equals(id))return 0xFF2C2536;return bg;}
        if("statut".equals(id))return 0xFFE3F3E9;if("prochaine".equals(id))return 0xFFF1E7FC;
        if(id.startsWith("repas"))return Color.WHITE;if("soutien".equals(id))return 0xFFF7DFEA;
        if("historique".equals(id))return 0xFFF7F0FB;return bg;
    }
    private android.graphics.drawable.Drawable style(String id){
        android.content.SharedPreferences p=ScheduleStore.prefs(this);
        int color=p.getInt("color_"+id,defaultColor(id));
        String pattern=p.getString("motif_"+id,"Uni");
        Bitmap img=loadPicture(p.getString("image_"+id,null));
        return new StyledDrawable(color,pattern,img,dark);
    }
    private LinearLayout card(String id){LinearLayout c=vertical();c.setPadding(dp(16),dp(15),dp(16),dp(15));c.setBackground(style(id));return c;}
    private String cardTitle(String id){for(int i=0;i<CARDS.length;i++)if(CARDS[i].equals(id))return CARD_NAMES[i];return id;}
    private void title(String heading,String subtitle){page.addView(text(heading,22,ink,true));gap(page,5);if(!subtitle.isEmpty())page.addView(text(subtitle,13,muted,false));gap(page,16);}
    @Override protected void onDestroy(){if(house!=null)house.close();super.onDestroy();}
    private void show(int index){
        if(index==5 && house!=null)return;
        if(house!=null){house.pause();house.close();house=null;}
        for(PetView view:petSurfaces)view.setForeground(false);petSurfaces.clear();
        tab=index;petView=null;petRefresh=0;lastEventRefresh=0;cachedEvents=null;theme();root=vertical();root.setBackground(style("fond"));setContentView(root);
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(navBg);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout head=vertical();head.setPadding(dp(20),dp(15),dp(18),dp(14));head.setBackgroundColor(dark?0xCC191520:0xDFFFFFFF);
        head.addView(text("✿  Mon Rythme Sleeve",20,accent,true));
        String date=LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM",Locale.FRANCE));
        head.addView(text(date.substring(0,1).toUpperCase(Locale.FRANCE)+date.substring(1),12,muted,false));root.addView(head);
        if(index==5){
            house=new PipouHouse(this);
            root.addView(house.game,new LinearLayout.LayoutParams(-1,0,1));
            house.resume();
        }else{
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=vertical();page.setPadding(dp(18),dp(14),dp(18),dp(26));
        scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(index==0)home();else if(index==1)planning();else if(index==2)history();else if(index==3)alerts();else settings();
        }
        LinearLayout nav=horizontal();nav.setPadding(dp(3),dp(7),dp(3),dp(7));nav.setBackgroundColor(navBg);
        for(int i=0;i<TABS.length;i++){
            final int target=i;LinearLayout cell=vertical();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(2),dp(4),dp(2),dp(4));
            if(i==tab)cell.setBackground(rounded(dark?0xFF49365C:0xFFF3E3F4,14));
            TextView glyph=text(ICONS[i],22,i==tab?accent:muted,true);glyph.setGravity(Gravity.CENTER);cell.addView(glyph);
            TextView word=text(TABS[i],10,i==tab?accent:muted,i==tab);word.setGravity(Gravity.CENTER);cell.addView(word);
            cell.setOnClickListener(v->show(target));nav.addView(cell,new LinearLayout.LayoutParams(0,dp(57),1));
        }root.addView(nav);
    }
    private String encouragement(){
        int hour=java.time.LocalTime.now().getHour();
        String[] morning={"Chaque petit pas compte. Avance à ton rythme ! 🌷","Une nouvelle journée pour prendre soin de toi. 💜","Aujourd'hui aussi, accorde-toi de la douceur. 🌸"};
        String[] afternoon={"Tu avances à ton rythme, et c'est ce qui compte. ✨","Prends ton temps, sois à l'écoute de toi. 💜","Un moment après l'autre. Tu fais de ton mieux ! 🌻"};
        String[] evening={"La journée se termine, prends soin de toi. 🌙","Prends un moment pour toi ce soir. 💜","Bonne soirée, et douceur pour la suite. ✨"};
        String[] a=hour<12?morning:hour<18?afternoon:evening;
        return a[Math.floorMod(welcomeVariant,a.length)];
    }
    private void home(){
        String first=ScheduleStore.prefs(this).getString("firstname","").trim();
        LinearLayout welcome=card("soutien");welcome.addView(text("Bonjour"+(first.isEmpty()?"":" "+first)+" ! 💜",24,ink,true));
        if(ScheduleStore.prefs(this).getBoolean("support",true)){gap(welcome,6);welcome.addView(text(encouragement(),14,muted,false));}
        add(page,welcome,15);
        LinearLayout current=card("statut");current.addView(text("MON STATUT ACTUEL",11,muted,true));gap(current,9);
        statusTitle=text("Chargement...",23,ink,true);current.addView(statusTitle);
        gap(current,5);statusDetail=text("",13,muted,false);current.addView(statusDetail);
        gap(current,11);countdown=text("--:--:--",28,ink,true);current.addView(countdown);add(page,current,12);
        LinearLayout upcoming=card("prochaine");upcoming.addView(text("PROCHAINE ÉTAPE",11,muted,true));gap(upcoming,7);
        nextLabel=text("",16,ink,true);upcoming.addView(nextLabel);add(page,upcoming,18);
        Button companion=button(PetStore.adopted(this)?"♡ Retrouver "+PetStore.name(this)+(PetStore.pending(this)>0?" · cadeaux disponibles 🎁":""):"♡ Adopter mon petit compagnon",false);
        companion.setOnClickListener(v->show(5));add(page,companion,16);
        page.addView(text("Mes "+ScheduleStore.count(this)+" prises alimentaires",18,ink,true));gap(page,11);
        listMeals(false);updateStatus();
        Button adjust=button("Modifier mon planning",true);adjust.setOnClickListener(v->show(1));add(page,adjust,10);
    }
    private void listMeals(boolean detailed){
        LocalDate d=ScheduleStore.day(this);int[] times=ScheduleStore.times(this);
        for(int i=0;i<ScheduleStore.count(this);i++){
            final int meal=i;HistoryStore.Entry entry=ScheduleStore.record(this,d,i);
            LinearLayout c=card("repas_"+i);LinearLayout row=horizontal();
            TextView mealName=text((i+1)+". "+ScheduleStore.name(i),15,ink,true);
            row.addView(mealName,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(text(ScheduleStore.display(times[i]),13,accent,true));c.addView(row);gap(c,7);
            if(entry!=null&&entry.started>0)c.addView(text("Début réel : "+ScheduleStore.displayTimestamp(entry.started),13,muted,false));
            if(entry!=null&&entry.finished>0)c.addView(text("Fin réelle : "+ScheduleStore.displayTimestamp(entry.finished)+" ✓",13,muted,false));
            if(entry==null||entry.started==0)c.addView(text("Fin prévue : "+ScheduleStore.display(times[i]+30),12,muted,false));
            else if(entry.finished==0)c.addView(text("Fin estimée : "+ScheduleStore.displayTimestamp(entry.started+30*60_000L),12,muted,false));
            if(detailed){gap(c,8);
                line(c,"Arrêt des boissons",ScheduleStore.display(times[i]-30));
                line(c,"Reprise des boissons",entry!=null&&entry.started>0&&entry.finished==0?"Après la fin réelle":ScheduleStore.displayTimestamp(ScheduleStore.expectedEnd(this,d,i)+30*60_000L));
            }
            if(entry==null||entry.finished==0){gap(c,10);LinearLayout actions=horizontal();
                if(entry==null||entry.started==0){Button start=button("Je commence",false);start.setOnClickListener(v->startMeal(meal));actions.addView(start,new LinearLayout.LayoutParams(0,dp(47),1));gapHorizontal(actions,7);}
                Button finish=button("J'ai terminé",true);finish.setOnClickListener(v->finishMeal(meal));actions.addView(finish,new LinearLayout.LayoutParams(0,dp(47),1));c.addView(actions);
            }
            gap(c,7);Button correct=button(entry!=null&&entry.started>0?"Corriger mon heure de début":"J'ai déjà commencé…",false);
            correct.setOnClickListener(v->correctMealStart(meal));add(c,correct,0);
            if(entry!=null&&(entry.started>0||entry.finished>0)){
                Button end=button("Modifier l'heure de fin",false);
                end.setOnClickListener(v->correctMealFinish(d,meal));gap(c,7);add(c,end,0);
            }
            add(page,c,10);
        }
    }
    private void gapHorizontal(LinearLayout box,int width){View v=new View(this);box.addView(v,new LinearLayout.LayoutParams(dp(width),1));}
    private void startMeal(int i){
        if(ScheduleStore.begin(this,i,System.currentTimeMillis())){
            if(PetStore.adopted(this))PetStore.care(this);
            AlarmScheduler.rescheduleAll(this);toast("Début réel enregistré. Horaires suivants recalculés.");show(tab);
        }else toast("Ce repas est déjà commencé ou indisponible.");
    }
    private void finishMeal(int i){
        HistoryStore.Entry entry=ScheduleStore.record(this,ScheduleStore.day(this),i);
        if(entry==null||entry.started==0){
            new AlertDialog.Builder(this).setTitle("Début non enregistré")
                .setMessage("Tu n'as pas appuyé sur « Je commence ». Enregistrer uniquement l'heure réelle de fin ?")
                .setPositiveButton("Oui",(d,w)->saveFinish(i)).setNegativeButton("Annuler",null).show();
        }else saveFinish(i);
    }
    private void saveFinish(int i){
        if(ScheduleStore.finish(this,i,System.currentTimeMillis())){
            if(PetStore.adopted(this))PetStore.care(this);
            AlarmScheduler.rescheduleAll(this);toast("Fin réelle enregistrée. Prochaine prise recalculée.");show(tab);
        }else toast("Impossible : la fin est antérieure au début, ou le repas est déjà terminé.");
    }
    private static final class NextEvent {
        long when;int kind;String label;
        NextEvent(long at,int type,String text){when=at;kind=type;label=text;}
    }
    private List<NextEvent> events(){
        List<NextEvent> out=new ArrayList<>();LocalDate d=ScheduleStore.day(this);
        for(int diff=0;diff<3;diff++){
            LocalDate day=d.plusDays(diff);int[] t=ScheduleStore.timesFor(this,day);
            for(int i=0;i<ScheduleStore.count(this);i++){
                HistoryStore.Entry entry=ScheduleStore.record(this,day,i);
                boolean started=entry!=null&&entry.started>0,finished=entry!=null&&entry.finished>0;
                if(!started&&!finished){out.add(new NextEvent(ScheduleStore.at(day,t[i]-30),0,"Arrêt boissons avant "+ScheduleStore.name(i)));
                    out.add(new NextEvent(ScheduleStore.at(day,t[i]),1,"Début : "+ScheduleStore.name(i)));}
                if(started&&!finished)out.add(new NextEvent(entry.started+30*60_000L,4,"Fin estimée : "+ScheduleStore.name(i)));
                if(!started||finished)out.add(new NextEvent(ScheduleStore.expectedEnd(this,day,i)+30*60_000L,3,"Reprise des boissons"));
            }
        }
        out.sort(Comparator.comparingLong(e->e.when));return out;
    }
    private void updateStatus(){
        if(tab!=0||countdown==null)return;
        long now=System.currentTimeMillis();
        if(cachedEvents==null||now-lastEventRefresh>30000L){
            cachedEvents=events();lastEventRefresh=now;cachedMealActive=false;
            LocalDate d=ScheduleStore.day(this);
            for(int i=0;i<ScheduleStore.count(this);i++){
                HistoryStore.Entry r=ScheduleStore.record(this,d,i);
                if(r!=null&&r.started>0&&r.finished==0){cachedMealActive=true;break;}
            }
        }
        NextEvent next=null,prev=null;
        for(NextEvent x:cachedEvents){if(x.when>now){next=x;break;}prev=x;}
        boolean active=cachedMealActive;
        if(active){statusTitle.setText("Repas en cours 🍽");statusDetail.setText("Appuie sur « J'ai terminé » à la fin réelle du repas.");}
        else if(prev!=null&&(prev.kind==0||prev.kind==1||prev.kind==4)){
            statusTitle.setText("Pause boisson ♡");statusDetail.setText("Attends la prochaine reprise des boissons.");
        }else{statusTitle.setText("Tu peux boire 💧");statusDetail.setText("Jusqu'à la prochaine pause prévue.");}
        if(next==null){countdown.setText("--:--:--");nextLabel.setText("Aucune étape programmée.");return;}
        long s=Math.max(0,(next.when-now+999)/1000);
        countdown.setText(String.format(Locale.FRANCE,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60));
        nextLabel.setText(ScheduleStore.displayTimestamp(next.when)+" · "+next.label);
    }
    private void line(LinearLayout parent,String key,String value){LinearLayout row=horizontal();
        row.addView(text(key,13,muted,false),new LinearLayout.LayoutParams(0,-2,1));row.addView(text(value,13,ink,true));parent.addView(row);gap(parent,5);
    }
    private void planning(){
        title("Mon planning 🌸","Choisis une prise de référence, puis adapte les horaires réels avec les boutons.");
        LinearLayout edit=card("prochaine");edit.addView(text("Prise de référence",14,ink,true));gap(edit,5);
        Spinner spinner=new Spinner(this);String[] names=new String[ScheduleStore.count(this)];
        System.arraycopy(ScheduleStore.NAMES,0,names,0,names.length);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,names){
            private View mealLabel(int position,View recycled,ViewGroup parent,boolean dropdown){
                TextView label=(TextView)(dropdown?super.getDropDownView(position,recycled,parent):super.getView(position,recycled,parent));
                boolean mainMeal=position==0||position==2||position==4;
                label.setTypeface(Typeface.DEFAULT,mainMeal?Typeface.BOLD:Typeface.NORMAL);
                label.setTextSize(mainMeal?16:14);label.setTextColor(mainMeal?ink:muted);
                label.setPadding(dp(14),dp(12),dp(14),dp(12));label.setMinHeight(dp(48));
                label.setBackgroundColor(dropdown?(mainMeal?(dark?0xFF41344C:0xFFF2E6F3):navBg):Color.TRANSPARENT);
                label.setContentDescription((mainMeal?"Repas principal : ":"")+getItem(position));
                return label;
            }
            @Override public View getView(int position,View recycled,ViewGroup parent){return mealLabel(position,recycled,parent,false);}
            @Override public View getDropDownView(int position,View recycled,ViewGroup parent){return mealLabel(position,recycled,parent,true);}
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);
        if(anchor>=names.length)anchor=4;spinner.setSelection(anchor);edit.addView(spinner,new LinearLayout.LayoutParams(-1,dp(46)));
        gap(edit,8);edit.addView(text("Horaire souhaité",14,ink,true));gap(edit,7);
        selectedMinute=ScheduleStore.times(this)[anchor];
        Button time=button(ScheduleStore.display(selectedMinute)+" · Modifier",false);
        time.setOnClickListener(v->{int current=Math.floorMod(selectedMinute,1440);
            new TimePickerDialog(this,(p,h,m)->{
                selectedMinute=h*60+m;
                if(anchor==5&&selectedMinute<ScheduleStore.times(this)[4])selectedMinute+=1440;
                time.setText(ScheduleStore.display(selectedMinute)+" · Modifier");
            },current/60,current%60,true).show();
        });
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> a,View v,int pos,long id){anchor=pos;selectedMinute=ScheduleStore.times(MainActivity.this)[pos];time.setText(ScheduleStore.display(selectedMinute)+" · Modifier");}
            @Override public void onNothingSelected(android.widget.AdapterView<?> a){}
        });
        add(edit,time,12);edit.addView(text("Intervalle après la fin",14,ink,true));gap(edit,5);
        RadioGroup gaps=new RadioGroup(this);gaps.setOrientation(RadioGroup.HORIZONTAL);chosenGap=ScheduleStore.gap(this);
        int[] values={150,165,180};String[] labels={"2 h 30","2 h 45","3 h"};
        for(int j=0;j<3;j++){final int value=values[j];RadioButton r=new RadioButton(this);
            r.setText(labels[j]);r.setTextSize(12);r.setTextColor(ink);r.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));r.setChecked(chosenGap==value);
            r.setOnClickListener(v->chosenGap=value);gaps.addView(r,new RadioGroup.LayoutParams(0,dp(45),1));
        }edit.addView(gaps);gap(edit,10);
        Button calculate=button("Recalculer mon planning",true);
        calculate.setOnClickListener(v->{
            if(ScheduleStore.saveAnchor(this,anchor,selectedMinute,chosenGap)){AlarmScheduler.rescheduleAll(this);toast("Nouveau planning enregistré.");show(1);}
            else new AlertDialog.Builder(this).setTitle("Horaires incompatibles")
                .setMessage("Le premier repas commencerait trop tôt ou le dîner prévu finirait après minuit. Choisis un autre horaire. La collation 3 peut, elle, dépasser minuit.")
                .setPositiveButton("OK",null).show();
        });add(edit,calculate,0);add(page,edit,17);
        page.addView(text("Mes horaires du jour",18,ink,true));gap(page,10);listMeals(true);
        page.addView(text("Un oubli ? Utilise « J'ai déjà commencé… » ou « Corriger mon heure de début ». Les horaires suivants et les alertes s'adaptent. Une fin réelle déjà enregistrée est conservée.",12,muted,false));
    }
    private void history(){
        title("Mon historique ◷","Débuts et fins réels de tes prises, conservés sur ton téléphone.");
        List<HistoryStore.Entry> records;try(HistoryStore db=new HistoryStore(this)){records=db.all();}
        if(records.isEmpty()){LinearLayout empty=card("historique");empty.addView(text("Aucun repas enregistré pour le moment.",15,ink,true));gap(empty,7);
            empty.addView(text("Utilise « Je commence » et « J'ai terminé » pour remplir ton historique.",13,muted,false));add(page,empty,12);return;}
        String previous="";
        for(HistoryStore.Entry r:records){
            if(!r.day.equals(previous)){
                if(!previous.isEmpty())gap(page,8);LocalDate d=LocalDate.parse(r.day);
                page.addView(text(d.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Locale.FRANCE)),17,accent,true));gap(page,8);
                previous=r.day;
            }
            LinearLayout c=card("historique");c.addView(text(ScheduleStore.name(r.meal),16,ink,true));gap(c,7);
            line(c,"Début réel",r.started>0?historyStamp(r,r.started):"Non enregistré");
            line(c,"Fin réelle",r.finished>0?historyStamp(r,r.finished):"En cours / non enregistrée");
            if(r.started>0&&r.finished>=r.started){long min=(r.finished-r.started)/60_000L;line(c,"Durée réelle",min+" min");}
            Button edit=button("Modifier l'heure de fin",false);
            edit.setOnClickListener(v->correctMealFinish(LocalDate.parse(r.day),r.meal));
            gap(c,8);add(c,edit,0);add(page,c,9);
        }
        page.addView(text("La collation après minuit reste classée dans la journée à laquelle elle appartient.",12,muted,false));
    }
    private String historyStamp(HistoryStore.Entry r,long millis){
        LocalDate entryDate=Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
        return ScheduleStore.displayTimestamp(millis)+(entryDate.isAfter(LocalDate.parse(r.day))?" (lendemain)":"");
    }
    private Switch toggle(String title,String desc,boolean checked,java.util.function.Consumer<Boolean> change){
        LinearLayout row=horizontal();row.setPadding(0,dp(6),0,dp(8));LinearLayout descCol=vertical();
        descCol.addView(text(title,15,ink,true));if(!desc.isEmpty()){gap(descCol,3);descCol.addView(text(desc,12,muted,false));}
        row.addView(descCol,new LinearLayout.LayoutParams(0,-2,1));Switch toggle=new Switch(this);toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((v,on)->change.accept(on));row.addView(toggle);page.addView(row);return toggle;
    }
    private void alerts(){
        title("Mes notifications ♡","Une alerte avec vibration par événement activé.");
        LinearLayout info=card("prochaine");info.addView(text("Alertes téléphone et montre",16,ink,true));gap(info,6);
        info.addView(text("Si ta Pixel Watch relaie les notifications de ton téléphone, elle pourra afficher ces alertes.",13,muted,false));add(page,info,16);
        toggle("Activer les notifications","Tous les rappels de repas et boissons",ScheduleStore.alertsEnabled(this),on->{ScheduleStore.prefs(this).edit().putBoolean("alerts",on).apply();AlarmScheduler.rescheduleAll(this);});
        String[] texts={"Arrêt des boissons","Début du repas","Reprise des boissons"};
        String[] details={"30 min avant chaque prise","À l'heure du repas","30 min après la fin réelle"};
        for(int i=0;i<3;i++){final int kind=i;toggle(texts[i],details[i],ScheduleStore.eventEnabled(this,i),on->{ScheduleStore.prefs(this).edit().putBoolean("event_"+kind,on).apply();AlarmScheduler.rescheduleAll(this);});}
        gap(page,14);Button permissions=button("Vérifier les autorisations Android",false);
        permissions.setOnClickListener(v->openPermissions());add(page,permissions,8);
        page.addView(text("Si un repas a commencé mais n'est pas terminé dans l'application, la reprise des boissons n'est pas annoncée prématurément.",12,muted,false));
    }
    private void openPermissions(){
        if(Build.VERSION.SDK_INT>=31){AlarmManager m=(AlarmManager)getSystemService(ALARM_SERVICE);
            if(m!=null&&!m.canScheduleExactAlarms()){
                try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));return;}
                catch(Exception ignored){}
            }
        }
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
    }
    private void settings(){
        title("Mes paramètres ⚙","Personnalise l'application à ton image.");
        LinearLayout identity=card("soutien");identity.addView(text("Mon prénom",16,ink,true));gap(identity,9);
        EditText first=new EditText(this);first.setSingleLine(true);first.setTextSize(16);first.setTextColor(ink);first.setHintTextColor(muted);
        first.setHint("Ton prénom");first.setText(ScheduleStore.prefs(this).getString("firstname",""));identity.addView(first);
        gap(identity,7);Button save=button("Enregistrer mon prénom",true);save.setOnClickListener(v->{
            ScheduleStore.prefs(this).edit().putString("firstname",first.getText().toString().trim()).apply();toast("Prénom enregistré !");
        });add(identity,save,0);add(page,identity,12);
        toggle("Messages d'encouragement","Une petite phrase bienveillante à l'ouverture",ScheduleStore.prefs(this).getBoolean("support",true),on->ScheduleStore.prefs(this).edit().putBoolean("support",on).apply());
        gap(page,13);
        LinearLayout look=card("prochaine");look.addView(text("Apparence et thèmes 🎨",17,ink,true));gap(look,10);
        Button themeButton=button("Mode clair / sombre / automatique",false);themeButton.setOnClickListener(v->chooseTheme());add(look,themeButton,9);
        Button accentButton=button("Couleur des boutons principaux",false);accentButton.setOnClickListener(v->chooseColor("button_primary"));add(look,accentButton,9);
        Button secondaryButton=button("Couleur des boutons secondaires",false);secondaryButton.setOnClickListener(v->chooseColor("button_secondary"));add(look,secondaryButton,9);
        Button customize=button("Personnaliser chaque cadre et fond",true);customize.setOnClickListener(v->chooseCard());add(look,customize,0);
        add(page,look,15);
        LinearLayout nutrition=card("repas_4");nutrition.addView(text("Mes prises alimentaires",17,ink,true));gap(nutrition,7);
        nutrition.addView(text("5 prises par défaut · 30 min chacune · arrêt/reprise boissons ±30 min",13,muted,false));gap(nutrition,7);
        add(page,nutrition,5);
        toggle("Activer la collation 3","Après le dîner, même après minuit",ScheduleStore.count(this)==6,on->{
            ScheduleStore.setSixth(this,on);AlarmScheduler.rescheduleAll(this);toast(on?"collation 3 activée":"collation 3 désactivée");show(4);
        });
        gap(page,9);
        LinearLayout hint=card("historique");hint.addView(text("Vie privée et sauvegarde",15,ink,true));gap(hint,6);
        hint.addView(text("Tes horaires, ton prénom et ton historique restent stockés localement. Les photos choisies ne sont pas envoyées sur Internet. Garde l'application V1 installée pendant les tests de cette bêta.",13,muted,false));
        gap(hint,9);hint.addView(text("Mon Rythme Sleeve · Version 3.0 bêta",12,muted,false));add(page,hint,10);
    }
    private void chooseTheme(){
        String[] choices={"Clair","Sombre","Automatique (selon le téléphone)"};String[] vals={"CLAIR","SOMBRE","AUTO"};
        new AlertDialog.Builder(this).setTitle("Apparence").setItems(choices,(d,choice)->{
            ScheduleStore.prefs(this).edit().putString("theme",vals[choice]).apply();show(4);
        }).show();
    }
    private void chooseCard(){
        new AlertDialog.Builder(this).setTitle("Quel cadre personnaliser ?").setItems(CARD_NAMES,(dialog,index)->{
            chosenCard=CARDS[index];chooseDecoration();
        }).show();
    }
    private void chooseDecoration(){
        String[] options={"Choisir une couleur","Choisir un motif (galerie intégrée)","Choisir une photo du téléphone","Réinitialiser ce cadre"};
        new AlertDialog.Builder(this).setTitle(cardTitle(chosenCard)).setItems(options,(dialog,index)->{
            if(index==0)chooseColor(chosenCard);
            else if(index==1)new AlertDialog.Builder(this).setTitle("Motifs disponibles").setItems(PATTERNS,(d,v)->{
                ScheduleStore.prefs(this).edit().putString("motif_"+chosenCard,PATTERNS[v]).remove("image_"+chosenCard).apply();show(4);
            }).show();
            else if(index==2){Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.setType("image/*");picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(picker,PICK_IMAGE);
            }else{ScheduleStore.prefs(this).edit().remove("color_"+chosenCard).remove("motif_"+chosenCard).remove("image_"+chosenCard).apply();show(4);}
        }).show();
    }
    private void chooseColor(String target){
        new AlertDialog.Builder(this).setTitle("Couleur pour : "+(target.startsWith("button_")?"boutons":cardTitle(target)))
            .setItems(new String[]{"Palette prédéfinie","Saisir un code couleur HEX"},(d,choice)->{
                if(choice==0)new AlertDialog.Builder(this).setTitle("Choisir une couleur").setItems(COLOR_NAMES,(x,i)->{
                    ScheduleStore.prefs(this).edit().putInt(target.startsWith("button_")?target:"color_"+target,COLORS[i]).apply();show(4);
                }).show();
                else{
                    EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT);
                    input.setHint("#E8D5F5");
                    LinearLayout holder=vertical();holder.setPadding(dp(20),0,dp(20),0);holder.addView(input);
                    new AlertDialog.Builder(this).setTitle("Couleur personnalisée").setView(holder)
                        .setPositiveButton("Appliquer",(x,y)->{
                            try{int color=Color.parseColor(input.getText().toString().trim());
                                ScheduleStore.prefs(this).edit().putInt(target.startsWith("button_")?target:"color_"+target,color).apply();show(4);
                            }catch(IllegalArgumentException ex){toast("Code couleur invalide. Exemple : #E8D5F5");}
                        }).setNegativeButton("Annuler",null).show();
                }
            }).show();
    }
    private void correctMealStart(int meal){
        LocalDate day=ScheduleStore.day(this);
        HistoryStore.Entry entry=ScheduleStore.record(this,day,meal);
        long initial=entry!=null&&entry.started>0?entry.started:ScheduleStore.at(day,ScheduleStore.times(this)[meal]);
        java.time.ZonedDateTime z=Instant.ofEpochMilli(initial).atZone(ZoneId.systemDefault());
        java.util.function.Consumer<LocalDate> choose=date->{
            TimePickerDialog picker=new TimePickerDialog(this,(view,h,m)->{
                long actual=ScheduleStore.at(date,h*60+m);
                new AlertDialog.Builder(this).setTitle("Confirmer le début réel")
                    .setMessage(ScheduleStore.name(meal)+" : "+date.format(DateTimeFormatter.ofPattern("dd/MM"))+" à "+ScheduleStore.hhmm(h*60+m)+".\n"+(entry!=null&&entry.finished>0?"La fin réelle reste inchangée.":"Fin estimée : "+ScheduleStore.displayTimestamp(actual+30*60_000L))+"\nLes prises suivantes non enregistrées et les alertes seront recalculées.")
                    .setPositiveButton("Enregistrer",(dialog,which)->{
                        String error=ScheduleStore.correctStart(this,day,meal,actual);
                        if(error!=null){toast(error);return;}
                        if(PetStore.adopted(this))PetStore.care(this);
                        AlarmScheduler.rescheduleAll(this);toast("Heure réelle corrigée.");show(tab);
                    }).setNegativeButton("Annuler",null).show();
            },z.getHour(),z.getMinute(),true);picker.setTitle("À quelle heure as-tu commencé ?");picker.show();
        };
        if(day.isBefore(LocalDate.now()))new AlertDialog.Builder(this).setTitle("Date du début réel")
            .setItems(new String[]{day.toString(),day.plusDays(1)+" (lendemain)"},(d,w)->choose.accept(day.plusDays(w))).show();
        else choose.accept(day);
    }
    private void correctMealFinish(LocalDate day,int meal){
        HistoryStore.Entry entry=ScheduleStore.record(this,day,meal);
        long initial=entry!=null&&entry.finished>0?entry.finished:System.currentTimeMillis();
        java.time.ZonedDateTime z=Instant.ofEpochMilli(initial).atZone(ZoneId.systemDefault());
        new AlertDialog.Builder(this).setTitle("Date de fin réelle")
            .setItems(new String[]{day+" (jour du planning)",day.plusDays(1)+" (lendemain)"},(dialog,choice)->{
                LocalDate date=day.plusDays(choice);
                new TimePickerDialog(this,(view,h,m)->{
                    long actual=ScheduleStore.at(date,h*60+m);
                    new AlertDialog.Builder(this).setTitle("Confirmer l'heure de fin")
                        .setMessage(ScheduleStore.name(meal)+" : "+date+" à "+ScheduleStore.hhmm(h*60+m)
                            +"\nReprise des boissons : "+ScheduleStore.displayTimestamp(actual+30*60_000L)
                            +"\nLes prises suivantes non enregistrées et les notifications seront ajustées. Les heures réelles déjà enregistrées seront conservées.")
                        .setPositiveButton("Enregistrer",(d,w)->{
                            String error=ScheduleStore.correctFinish(this,day,meal,actual);
                            if(error!=null){toast(error);return;}
                            AlarmScheduler.rescheduleAll(this);toast("Heure de fin corrigée.");show(tab);
                        }).setNegativeButton("Annuler",null).show();
                },z.getHour(),z.getMinute(),true).show();
            }).setNegativeButton("Annuler",null).show();
    }
    private void showAdoption(){
        for(PetView view:petSurfaces)view.setForeground(false);petSurfaces.clear();petView=null;
        page.removeAllViews();title("Choisis ton compagnon","Tes jours ensemble et tes cadeaux sont conservés.");adoptionGallery();
    }
    private void drinkWithPet(){
        PetStore.State state=PetStore.state(this,System.currentTimeMillis());
        if(!state.drink){toast("C'est la pause boissons. Ton compagnon attend aussi.");petRefresh=0;refreshPet();return;}
        PetStore.prefs(this).edit().putLong("drink",System.currentTimeMillis()).apply();PetStore.care(this);playPet(2);toast("Ta prise de boisson est enregistrée.");
    }
    private void eatWithPet(){
        PetStore.State state=PetStore.state(this,System.currentTimeMillis());
        if(state.active){show(0);return;}
        if(state.meal<0){toast("La prochaine prise n'est pas encore prévue.");return;}
        final int meal=state.meal;
        new AlertDialog.Builder(this).setTitle(ScheduleStore.name(meal))
            .setItems(new String[]{"Je commence maintenant","J'ai déjà commencé"},(d,w)->{
                if(w==1){correctMealStart(meal);return;}
                if(ScheduleStore.begin(this,meal,System.currentTimeMillis())){
                    PetStore.care(this);AlarmScheduler.rescheduleAll(this);petRefresh=0;playPet(1);toast("Repas commencé pour vous deux. Pense à enregistrer la fin réelle.");
                }else{toast("Cette prise a déjà été enregistrée.");petRefresh=0;refreshPet();}
            }).show();
    }
    private void restPet(){
        if(PetStore.state(this,System.currentTimeMillis()).active){toast("On termine d'abord le repas tranquillement.");return;}
        playPet(5);
    }
    private int rewardImage(boolean decor,int item){
        int[] images=decor?new int[]{R.drawable.reward_decor_0,R.drawable.reward_decor_1,R.drawable.reward_decor_2,R.drawable.reward_decor_3}:new int[]{R.drawable.reward_accessory_0,R.drawable.reward_accessory_1,R.drawable.reward_accessory_2,R.drawable.reward_accessory_3,R.drawable.reward_accessory_4,R.drawable.reward_accessory_5};
        return images[Math.floorMod(item,images.length)];
    }
    private void rewardCard(LinearLayout parent,boolean decor,int item,boolean available){
        LocalDate adopted=PetStore.adoption(this),today=LocalDate.now();
        LocalDate due=decor?PetRules.nextSpecial(adopted,item):PetRules.nextAccessory(adopted,item);
        LocalDate from=decor?(item==0?adopted:PetRules.nextSpecial(adopted,item-1)):(item==0?adopted:PetRules.nextAccessory(adopted,item-1));
        LinearLayout row=horizontal();row.setPadding(0,dp(8),0,dp(8));
        android.widget.ImageView image=new android.widget.ImageView(this);image.setImageResource(rewardImage(decor,item));image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);image.setContentDescription("Aperçu de "+(decor?PetStore.decor(item):PetStore.accessory(item)));row.addView(image,new LinearLayout.LayoutParams(dp(88),dp(88)));gapHorizontal(row,12);
        LinearLayout detail=vertical();detail.addView(text(decor?PetStore.decor(item):PetStore.accessory(item),15,ink,true));gap(detail,4);
        detail.addView(text(available?"Disponible : ouvre tes cadeaux":due.format(DateTimeFormatter.ofPattern("EEEE d MMMM",Locale.FRANCE)),12,muted,false));
        android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);int progress=PetRules.progress(from,due,today);bar.setProgress(progress);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));bar.setContentDescription("Progression : "+progress+" %");detail.addView(bar,new LinearLayout.LayoutParams(-1,dp(16)));
        long left=Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(today,due));detail.addView(text(available?"Cadeau débloqué":progress+" % · "+left+" jour(s) restant(s)",12,muted,false));row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
        row.setOnClickListener(v->previewReward(decor,item,false));parent.addView(row);
    }
    private void refreshRewards(){
        if(rewardPanel==null)return;android.content.SharedPreferences p=PetStore.prefs(this);
        String stamp=LocalDate.now()+":"+p.getInt("claimed_a",0)+":"+p.getInt("claimed_s",0);
        if(stamp.equals(rewardStamp))return;rewardStamp=stamp;rewardPanel.removeAllViews();
        int a=PetStore.accessories(this),s=PetStore.specials(this);
        if(a>p.getInt("claimed_a",0))rewardCard(rewardPanel,false,p.getInt("claimed_a",0),true);
        if(s>p.getInt("claimed_s",0))rewardCard(rewardPanel,true,p.getInt("claimed_s",0),true);
        rewardPanel.addView(text("Prochains cadeaux à débloquer",15,ink,true));
        rewardCard(rewardPanel,false,a,false);rewardCard(rewardPanel,false,a+1,false);rewardCard(rewardPanel,true,s,false);
    }
    private void previewReward(boolean decor,int item,boolean owned){
        LinearLayout detail=vertical();detail.setPadding(dp(18),dp(8),dp(18),dp(12));
        android.widget.ImageView image=new android.widget.ImageView(this);image.setImageResource(rewardImage(decor,item));image.setAdjustViewBounds(true);detail.addView(image,new LinearLayout.LayoutParams(-1,dp(240)));
        detail.addView(text(decor?"Ambiance de la maison : textiles, couleurs et petits objets décoratifs.":"Accessoire en volume, porté par ton compagnon.",14,muted,false));gap(detail,6);
        detail.addView(text("Aperçu de la première édition. Les éditions suivantes déclinent les couleurs.",12,muted,false));
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(decor?PetStore.decor(item):PetStore.accessory(item)).setView(detail).setNegativeButton("Fermer",null);
        if(owned)dialog.setPositiveButton(decor?"Installer":"Porter",(d,w)->{PetStore.prefs(this).edit().putInt(decor?"decor":"accessory",item).apply();playPet(4);});
        dialog.show();
    }
    private void adoptionGallery(){
        int[] portraits={R.drawable.companion_0,R.drawable.companion_1,R.drawable.companion_2,R.drawable.companion_3,R.drawable.companion_4,R.drawable.companion_5,R.drawable.companion_6,R.drawable.companion_7};
        for(int row=0;row<4;row++){
            LinearLayout pair=horizontal();for(int col=0;col<2;col++){
                final int species=row*2+col;LinearLayout cell=vertical();cell.setPadding(dp(5),dp(8),dp(5),dp(8));
                android.widget.ImageView preview=new android.widget.ImageView(this);preview.setImageResource(portraits[species]);preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);preview.setContentDescription(PetStore.NAMES[species]);cell.addView(preview,new LinearLayout.LayoutParams(-1,dp(145)));
                Button choose=button(PetStore.NAMES[species],false);choose.setOnClickListener(v->namePet(species));cell.addView(choose,new LinearLayout.LayoutParams(-1,-2));preview.setOnClickListener(v->namePet(species));pair.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
            }add(page,pair,7);
        }
    }
    private void namePet(int species){
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Son petit prénom");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
        if(PetStore.adopted(this))name.setText(PetStore.name(this));
        new AlertDialog.Builder(this).setTitle(PetStore.NAMES[species]).setView(name).setPositiveButton("C'est mon compagnon !",(d,w)->{PetStore.adopt(this,species,name.getText().toString());try{CompanionIcon.apply(this,species);}catch(RuntimeException ex){toast("Le compagnon est enregistré. L’icône sera réessayée au prochain lancement.");}petAction=4;petActionUntil=System.currentTimeMillis()+4000;show(5);}).setNegativeButton("Annuler",null).show();
    }
    private void playPet(int action){petAction=action;petActionUntil=System.currentTimeMillis()+(action==5?12000:6500);petRefresh=0;refreshPet();}
    private void refreshPet(){
        if(tab!=5||petView==null||petMessage==null)return;
        long now=System.currentTimeMillis();if(now-petRefresh<1000)return;petRefresh=now;
        PetStore.State state=PetStore.state(this,now);android.content.SharedPreferences p=PetStore.prefs(this);
        int hour=java.time.LocalTime.now().getHour();int pose=state.active?1:state.sulk?3:hour<7?5:0;
        if(now<petActionUntil&&(!state.active||petAction==1))pose=petAction;
        petView.configure(pose,p.getInt("accessory",-1),p.getInt("decor",-1),p.getBoolean("motion",true));
        petMessage.setText(pose==5?"Un petit moment de repos dans mon lit…":pose==2?"À notre santé, avec mon verre d'eau !":pose==4?"Quel bonheur de te retrouver !":state.message);
        petDrink.setEnabled(state.drink);petDrink.setAlpha(state.drink?1f:.5f);petDrink.setText(state.drink?"On boit 💧":"Pause boissons · on attend");
        petEat.setEnabled(state.active||state.meal>=0);petEat.setAlpha(state.active||state.meal>=0?1f:.5f);petEat.setText(state.active?"Enregistrer la fin du repas":state.meal>=0?"On mange · "+ScheduleStore.name(state.meal):"On mangera à la prochaine prise");
        String lastDrink=p.getLong("drink",0)>0?"\nDernière boisson : "+Instant.ofEpochMilli(p.getLong("drink",0)).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM à HH:mm")):"";
        refreshRewards();
        petRewards.setText(PetStore.pending(this)+" cadeau(x) à ouvrir · "+p.getInt("claimed_a",0)+" accessoires · "+p.getInt("claimed_s",0)+" décors"+lastDrink);
    }
    private void collection(boolean decor,int offset){
        android.content.SharedPreferences p=PetStore.prefs(this);int count=p.getInt(decor?"claimed_s":"claimed_a",0),end=Math.min(count,offset+12);
        LinearLayout items=vertical();items.setPadding(dp(14),dp(8),dp(14),dp(8));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle((decor?"Décors":"Accessoires")+" · "+count+" débloqué(s)").setNegativeButton("Fermer",null).create();
        Button remove=button(decor?"Maison d'origine":"Sans accessoire",false);remove.setOnClickListener(v->{p.edit().putInt(decor?"decor":"accessory",-1).apply();dialog.dismiss();playPet(4);});add(items,remove,8);
        if(count==0)items.addView(text("Tes cadeaux apparaîtront ici après leur ouverture.",14,muted,false));
        for(int i=offset;i<end;i++){
            final int item=i;LinearLayout row=horizontal();android.widget.ImageView image=new android.widget.ImageView(this);image.setImageResource(rewardImage(decor,i));row.addView(image,new LinearLayout.LayoutParams(dp(72),dp(72)));gapHorizontal(row,10);
            TextView label=text((decor?PetStore.decor(i):PetStore.accessory(i))+(p.getInt(decor?"decor":"accessory",-1)==i?" · installé":""),14,ink,true);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->{dialog.dismiss();previewReward(decor,item,true);});items.addView(row);
        }
        if(end<count){Button next=button("Suivants",false);next.setOnClickListener(v->{dialog.dismiss();collection(decor,end);});add(items,next,8);}
        if(offset>0){Button previous=button("Précédents",false);previous.setOnClickListener(v->{dialog.dismiss();collection(decor,Math.max(0,offset-12));});add(items,previous,8);}
        ScrollView scroll=new ScrollView(this);scroll.addView(items);dialog.setView(scroll);dialog.show();
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}

