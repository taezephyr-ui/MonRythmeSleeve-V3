package fr.monrythmesleeve.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

/** One enabled launcher alias. The real activity always remains enabled. */
public final class CompanionIcon {
    private static final String PREFIX="fr.monrythmesleeve.app.Launcher";
    private CompanionIcon(){}
    public static void apply(Context c,int species){
        if(species<0||species>7)return;
        PackageManager pm=c.getPackageManager();String target=PREFIX+species;
        boolean unchanged=true;
        for(int i=-1;i<8;i++){
            String name=i<0?PREFIX+"Default":PREFIX+i;
            int expected=name.equals(target)?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            if(pm.getComponentEnabledSetting(new ComponentName(c.getPackageName(),name))!=expected)unchanged=false;
        }
        if(unchanged)return;
        if(Build.VERSION.SDK_INT>=33){
            List<PackageManager.ComponentEnabledSetting> changes=new ArrayList<>();
            for(int i=-1;i<8;i++){
                String name=i<0?PREFIX+"Default":PREFIX+i;
                changes.add(new PackageManager.ComponentEnabledSetting(new ComponentName(c.getPackageName(),name),
                    name.equals(target)?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP));
            }
            pm.setComponentEnabledSettings(changes);
        }else{
            // Enable first, so there is never a moment without a launcher entry.
            pm.setComponentEnabledSetting(new ComponentName(c.getPackageName(),target),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
            for(int i=-1;i<8;i++){
                String name=i<0?PREFIX+"Default":PREFIX+i;
                if(!name.equals(target))pm.setComponentEnabledSetting(new ComponentName(c.getPackageName(),name),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
            }
        }
    }
}
