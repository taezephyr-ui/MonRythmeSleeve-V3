package fr.monrythmesleeve.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Six illustrated poses per animal, animated locally without network access. */
public final class PetView extends View {
    private static Bitmap atlas;
    private static final int[] ROWS={0,183,348,514,686,862,1028,1215,1405};
    private static final int[] COLS={0,185,365,544,717,897,1082};
    private static final int[] PALETTE={0xFFD87AA8,0xFF9A83D1,0xFF5AABC0,0xFF81AD7A,0xFFE1A14E,0xFFDC8391};
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private ValueAnimator animator;
    private float phase;
    private int species,pose,accessory=-1,decor=-1;
    private boolean motion=true;
    public PetView(Context c,int species){super(c);this.species=species;
        if(atlas==null)atlas=BitmapFactory.decodeResource(getResources(),R.drawable.pet_atlas);
        setContentDescription(PetStore.NAMES[species]);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    public void configure(int state,int item,int scene,boolean animate){
        pose=state;accessory=item;decor=scene;motion=animate;
        setContentDescription(PetStore.NAMES[species]+" : "+new String[]{"heureux","mange","boit","boude","fait la fête","dort"}[state]);
        syncAnimation();invalidate();
    }
    private void syncAnimation(){
        boolean run=motion&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown()&&ValueAnimator.areAnimatorsEnabled();
        if(run&&animator==null){animator=ValueAnimator.ofFloat(0,6.283185f);animator.setDuration(2400);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setInterpolator(new LinearInterpolator());animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});animator.start();}
        else if(!run&&animator!=null){animator.cancel();animator=null;phase=0;}
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();syncAnimation();}
    @Override protected void onDetachedFromWindow(){if(animator!=null){animator.cancel();animator=null;}super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int v){super.onWindowVisibilityChanged(v);syncAnimation();}
    private void color(int c){p.setColor(c);p.setStyle(Paint.Style.FILL);}
    private void circle(Canvas c,float x,float y,float r,int col){color(col);c.drawCircle(x,y,r,p);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
        float scale=Math.min(getWidth()/320f,getHeight()/270f);canvas.save();canvas.translate((getWidth()-320*scale)/2,(getHeight()-270*scale)/2);canvas.scale(scale,scale);
        color(0xFFFFFAF5);canvas.drawRoundRect(0,0,320,270,24,24,p);
        if(decor>=0)drawDecor(canvas);
        float bob=motion?(float)Math.sin(phase)*(pose==4?8:pose==1||pose==2?2:3):0;
        canvas.save();canvas.translate(0,bob);
        if(pose==3)canvas.rotate((float)Math.sin(phase)*2,160,200);
        float breath=1+(motion?(float)Math.sin(phase)*.012f:0);canvas.scale(1,breath,160,220);
        if(atlas!=null){Rect src=new Rect(COLS[pose],ROWS[species],COLS[pose+1],ROWS[species+1]);float ratio=src.width()/(float)src.height();float h=195,w=h*ratio;canvas.drawBitmap(atlas,src,new RectF(160-w/2,35,160+w/2,230),p);}
        if(accessory>=0&&pose!=5)drawAccessory(canvas);
        canvas.restore();
        if(pose==4){for(int i=0;i<8;i++){float x=24+i*39,y=25+(float)Math.sin(phase+i)*15;circle(canvas,x,y,3,PALETTE[i%6]);}}
        if(pose==5){color(0xFF9A83D1);p.setTextSize(18);canvas.drawText("z Z",239,60+bob,p);}
        canvas.restore();
    }
    private void drawDecor(Canvas c){
        int theme=decor%4,tint=PALETTE[(decor/4)%6];
        for(int i=0;i<7;i++){
            float x=i%2==0?18:300,y=30+i*32;
            if(theme==0){for(int j=0;j<5;j++)circle(c,x+(float)Math.cos(j*1.256)*7,y+(float)Math.sin(j*1.256)*7,4,tint);circle(c,x,y,3,0xFFEBC568);}
            else if(theme==1){color(tint);p.setTextSize(20);c.drawText("✦",x-8,y,p);}
            else if(theme==2){color(tint);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawArc(x-10,y-5,x+10,y+5,0,180,false,p);p.setStyle(Paint.Style.FILL);}
            else{color(tint);c.drawOval(x-8,y-12,x+8,y+12,p);}
        }
        color(tint);p.setAlpha(90);c.drawRoundRect(14,248,306,255,4,4,p);p.setAlpha(255);
    }
    private void drawAccessory(Canvas c){
        int kind=accessory%6,col=PALETTE[(accessory/6)%6];color(col);
        if(kind==0){Path bow=new Path();bow.moveTo(160,155);bow.lineTo(141,145);bow.lineTo(141,164);bow.close();bow.moveTo(160,155);bow.lineTo(179,145);bow.lineTo(179,164);bow.close();c.drawPath(bow,p);circle(c,160,155,5,0xFFFFD67C);}
        else if(kind==1){Path crown=new Path();crown.moveTo(139,66);crown.lineTo(135,43);crown.lineTo(151,54);crown.lineTo(160,36);crown.lineTo(170,54);crown.lineTo(184,43);crown.lineTo(180,66);crown.close();c.drawPath(crown,p);}
        else if(kind==2){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawCircle(140,111,13,p);c.drawCircle(180,111,13,p);c.drawLine(153,111,167,111,p);p.setStyle(Paint.Style.FILL);}
        else if(kind==3){c.drawRoundRect(134,151,184,164,5,5,p);c.drawRoundRect(170,159,182,192,4,4,p);}
        else if(kind==4){for(int i=0;i<5;i++)circle(c,199+(float)Math.cos(i*1.256)*9,83+(float)Math.sin(i*1.256)*9,7,col);circle(c,199,83,5,0xFFFFD67C);}
        else{c.drawRoundRect(137,37,182,67,7,7,p);c.drawOval(125,61,194,72,p);}
    }
}
