package fr.monrythmesleeve.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Fonds decoratifs locaux : fleurs, feuilles, bulles, etoiles, vagues, degrades et photos personnelles. */
public final class StyledDrawable extends Drawable {
    private final int base;
    private final String motif;
    private final Bitmap picture;
    private final boolean dark;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density=android.content.res.Resources.getSystem().getDisplayMetrics().density;
    public StyledDrawable(int color,String style,Bitmap photo,boolean darkMode){base=color;motif=style;picture=photo;dark=darkMode;}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();float w=b.width(),h=b.height();if(w<=0||h<=0)return;
        float r=24*density;
        RectF bounds=new RectF(b);Path clipping=new Path();clipping.addRoundRect(bounds,r,r,Path.Direction.CW);
        canvas.save();canvas.clipPath(clipping);
        p.setShader(null);p.setColor(base);p.setStyle(Paint.Style.FILL);canvas.drawRect(bounds,p);
        if("Dégradé".equals(motif)){
            int other=dark?blend(base,Color.BLACK,.28f):blend(base,Color.WHITE,.48f);
            p.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,base,other,Shader.TileMode.CLAMP));
            canvas.drawRect(bounds,p);p.setShader(null);
        }
        if(picture!=null&&!picture.isRecycled()){
            float ratio=Math.max(w/picture.getWidth(),h/picture.getHeight());
            float dw=picture.getWidth()*ratio,dh=picture.getHeight()*ratio;
            RectF dst=new RectF(b.left+(w-dw)/2,b.top+(h-dh)/2,b.left+(w+dw)/2,b.top+(h+dh)/2);
            p.setColor(Color.WHITE);canvas.drawBitmap(picture,null,dst,p);
            p.setColor(dark?0xAA100C21:0xBBFFFFFF);canvas.drawRect(bounds,p);
        } else if(!"Uni".equals(motif)&&!"Dégradé".equals(motif)){
            int accent=dark?0x55F5D8FC:0x669C4C8E;
            for(int i=0;i<15;i++){
                float x=b.left+((i*97+37)%101)/100f*w;
                float y=b.top+((i*61+23)%97)/96f*h;
                float size=5*density+(i%3)*3*density;
                if("Fleurs".equals(motif))flower(canvas,x,y,size,accent);
                else if("Feuilles".equals(motif))leaf(canvas,x,y,size,accent);
                else if("Bulles".equals(motif)){p.setColor(accent);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f*density);canvas.drawCircle(x,y,size,p);p.setStyle(Paint.Style.FILL);}
                else if("Étoiles".equals(motif))star(canvas,x,y,size,accent);
                else if("Vagues".equals(motif)){p.setColor(accent);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*density);Path wave=new Path();wave.moveTo(x-size*2,y);wave.quadTo(x-size,y-size,x,y);wave.quadTo(x+size,y+size,x+size*2,y);canvas.drawPath(wave,p);p.setStyle(Paint.Style.FILL);}
            }
        }
        // Maintient un contraste lisible meme pour des couleurs choisies tres claires/sombres.
        if(picture==null){
            float luminance=Color.luminance(base);
            int overlay=!dark&&luminance<0.24?0xD4FFFFFF:dark&&luminance>0.62?0xC0181420:Color.TRANSPARENT;
            if(overlay!=Color.TRANSPARENT){p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(overlay);canvas.drawRect(bounds,p);}
        }
        canvas.restore();
    }
    private void flower(Canvas c,float x,float y,float s,int color){
        p.setColor(color);p.setStyle(Paint.Style.FILL);
        for(int i=0;i<5;i++){double angle=i*2*Math.PI/5;p.setColor(color);c.drawCircle(x+(float)Math.cos(angle)*s*.75f,y+(float)Math.sin(angle)*s*.75f,s*.52f,p);}
        p.setColor(dark?0x88FFE6AC:0x88F8C16D);c.drawCircle(x,y,s*.36f,p);
    }
    private void leaf(Canvas c,float x,float y,float s,int color){
        p.setColor(color);c.save();c.rotate(35,x,y);c.drawOval(new RectF(x-s/2,y-s*1.3f,x+s/2,y+s*1.3f),p);c.restore();
    }
    private void star(Canvas c,float x,float y,float s,int color){
        p.setColor(color);Path path=new Path();for(int i=0;i<10;i++){
            double a=-Math.PI/2+i*Math.PI/5;float radius=i%2==0?s:s*.38f;
            float px=x+(float)Math.cos(a)*radius,py=y+(float)Math.sin(a)*radius;
            if(i==0)path.moveTo(px,py);else path.lineTo(px,py);
        }path.close();c.drawPath(path,p);
    }
    private int blend(int a,int b,float fraction){return Color.rgb((int)(Color.red(a)*(1-fraction)+Color.red(b)*fraction),(int)(Color.green(a)*(1-fraction)+Color.green(b)*fraction),(int)(Color.blue(a)*(1-fraction)+Color.blue(b)*fraction));}
    @Override public void setAlpha(int alpha) {p.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

