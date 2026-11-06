package fr.monrythmesleeve.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Native 3D view; animation is capped at 30 fps and stopped outside this screen. */
public final class PetView extends GLSurfaceView {
    private final RoomRenderer scene;
    private final Handler frames=new Handler(Looper.getMainLooper());
    private boolean motion=true,foreground=true,attached=false;
    private float downX,downY;
    private java.util.function.Consumer<String> interaction;
    private final Runnable frame=new Runnable(){public void run(){
        if(!attached||!foreground||!isShown())return;
        requestRender();if(motion&&ValueAnimator.areAnimatorsEnabled())frames.postDelayed(this,33);
    }};
    public PetView(Context c,int species){super(c);
        setEGLContextClientVersion(2);setPreserveEGLContextOnPause(true);
        scene=new RoomRenderer(Math.max(0,Math.min(7,species)));
        setRenderer(scene);setRenderMode(RENDERMODE_WHEN_DIRTY);
        setContentDescription(PetStore.NAMES[species]+" dans sa maison. Toucher la table, le verre, le lit ou le compagnon. Les boutons sous la scène proposent les mêmes actions.");
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);setClickable(true);
    }
    public void setInteraction(java.util.function.Consumer<String> callback){interaction=callback;}
    public void configure(int pose,int item,int decor,boolean animate){
        motion=animate;scene.pose=pose;scene.accessory=item;scene.decor=decor;
        scene.motion=animate&&ValueAnimator.areAnimatorsEnabled();restartFrames();
    }
    public void rest(boolean value){scene.rest=value;requestRender();}
    public void setForeground(boolean value){
        if(foreground!=value){foreground=value;if(value)onResume();else onPause();}restartFrames();
    }
    private void restartFrames(){frames.removeCallbacks(frame);if(attached&&foreground&&isShown())frames.post(frame);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;restartFrames();}
    @Override protected void onDetachedFromWindow(){attached=false;frames.removeCallbacks(frame);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int v){super.onWindowVisibilityChanged(v);if(scene!=null)restartFrames();}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP){
            int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if(Math.hypot(e.getX()-downX,e.getY()-downY)<=slop){
                if(interaction!=null)interaction.accept(scene.hit(e.getX()/getWidth(),e.getY()/getHeight()));else performClick();
            }return true;
        }return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
