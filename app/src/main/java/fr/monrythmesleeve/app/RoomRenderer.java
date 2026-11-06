package fr.monrythmesleeve.app;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Local mesh scene: lit volumes, procedural wood / woven fabric, articulated animals. */
public final class RoomRenderer implements GLSurfaceView.Renderer {
    public volatile int pose,accessory=-1,decor=-1;
    public volatile boolean motion=true,rest=false;
    private final int species;
    private final float[] vp=new float[16],model=new float[16],mvp=new float[16],normal=new float[16],inverse=new float[16];
    private int program,posLoc,normLoc,mvpLoc,modelLoc,normalLoc,colorLoc,textureLoc;
    private Mesh sphere,cube,cylinder;
    private long lastFrame;
    private float time,petX=.4f,petZ=.6f,facing,baseY,angle,breath;
    private int material;
    private volatile float[] hotspots=new float[8];
    private static final int CREAM=0xFFF9E8CC,WOOD=0xFFB97F52,WHITE=0xFFFFFAF1,DARK=0xFF342C44,PINK=0xFFE8A0B9;
    private static final int[] FUR={0xFFA58CDD,0xFFF2AABB,0xFFA97F61,0xFFB5BDD2,0xFFCD7543,0xFFF2B3CC,0xFFE8D7BC,0xFFBA9874};
    private static final int[] TINTS={0xFFD78FAD,0xFF8D9EC7,0xFF76B5BC,0xFF8FAF8A,0xFFDDB26D,0xFFB69AC8};
    private static final String VERTEX="uniform mat4 uMVP;uniform mat4 uModel;uniform mat4 uNormal;attribute vec3 aPos;attribute vec3 aNorm;varying vec3 vP;varying vec3 vN;void main(){vP=(uModel*vec4(aPos,1.)).xyz;vN=normalize((uNormal*vec4(aNorm,0.)).xyz);gl_Position=uMVP*vec4(aPos,1.);}";
    private static final String FRAGMENT="precision mediump float;uniform vec3 uColor;uniform float uTexture;varying vec3 vP;varying vec3 vN;void main(){vec3 n=normalize(vN);float l=max(dot(n,normalize(vec3(-3.,7.,5.))),0.);float grain=1.;if(uTexture>0.5&&uTexture<1.5){grain=.91+.09*sin(vP.x*16.+sin(vP.z*3.)*2.);grain*=.97+.03*sin(vP.z*35.);}if(uTexture>1.5){grain=.94+.06*sin(vP.x*65.)*sin(vP.y*65.+vP.z*65.);}vec3 c=uColor*grain*(.52+.48*l);float shine=pow(max(dot(n,normalize(vec3(-1.,2.,4.))),0.),20.);gl_FragColor=vec4(c+vec3(.06)*shine,1.);}";
    public RoomRenderer(int kind){species=kind;}
    private static int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
        program=GLES20.glCreateProgram();int v=shader(GLES20.GL_VERTEX_SHADER,VERTEX),f=shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT);GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);
        int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
        posLoc=GLES20.glGetAttribLocation(program,"aPos");normLoc=GLES20.glGetAttribLocation(program,"aNorm");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");modelLoc=GLES20.glGetUniformLocation(program,"uModel");normalLoc=GLES20.glGetUniformLocation(program,"uNormal");colorLoc=GLES20.glGetUniformLocation(program,"uColor");textureLoc=GLES20.glGetUniformLocation(program,"uTexture");
        sphere=Mesh.sphere(18,12);cube=Mesh.cube();cylinder=Mesh.cylinder(24);GLES20.glEnable(GLES20.GL_DEPTH_TEST);lastFrame=0;
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){
        GLES20.glViewport(0,0,w,h);float aspect=w/(float)Math.max(1,h),halfWidth=Math.max(4.2f,3.5f*aspect),halfHeight=halfWidth/aspect;
        float[] view=new float[16],projection=new float[16];Matrix.orthoM(projection,0,-halfWidth,halfWidth,-halfHeight,halfHeight,1,40);Matrix.setLookAtM(view,0,7,6.8f,10,0,.8f,0,0,1,0);Matrix.multiplyMM(vp,0,projection,0,view,0);
    }
    @Override public void onDrawFrame(GL10 gl){
        long now=SystemClock.uptimeMillis();float dt=lastFrame==0?0:Math.min(.06f,(now-lastFrame)/1000f);lastFrame=now;if(motion)time+=dt;
        GLES20.glClearColor(.94f,.90f,.87f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program);
        int state=rest?5:pose;
        float targetX=state==1||state==2?-1.15f:state==5?1.65f:.4f+(motion?(float)Math.sin(time*.22f)*.8f:0);
        float targetZ=state==1||state==2?-.82f:state==5?-1.1f:.6f+(motion?(float)Math.cos(time*.3f)*.4f:0);
        if(motion){float blend=Math.min(1,dt*2.4f);petX+=(targetX-petX)*blend;petZ+=(targetZ-petZ)*blend;}else{petX=targetX;petZ=targetZ;}
        facing=state==1||state==2?0:state==5?15:(float)Math.sin(time*.22f)*18;
        room();animal(state);setHotspots();
    }
    private void draw(Mesh mesh,float x,float y,float z,float sx,float sy,float sz,int color,int texture,float rx,float ry,float rz){
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);if(ry!=0)Matrix.rotateM(model,0,ry,0,1,0);if(rx!=0)Matrix.rotateM(model,0,rx,1,0,0);if(rz!=0)Matrix.rotateM(model,0,rz,0,0,1);Matrix.scaleM(model,0,sx,sy,sz);Matrix.multiplyMM(mvp,0,vp,0,model,0);Matrix.invertM(inverse,0,model,0);Matrix.transposeM(normal,0,inverse,0);
        GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniformMatrix4fv(modelLoc,1,false,model,0);GLES20.glUniformMatrix4fv(normalLoc,1,false,normal,0);GLES20.glUniform3f(colorLoc,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f);GLES20.glUniform1f(textureLoc,texture);
        mesh.data.position(0);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,24,mesh.data);mesh.data.position(3);GLES20.glVertexAttribPointer(normLoc,3,GLES20.GL_FLOAT,false,24,mesh.data);GLES20.glEnableVertexAttribArray(posLoc);GLES20.glEnableVertexAttribArray(normLoc);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,mesh.count);
    }
    private void ball(float x,float y,float z,float a,float b,float c,int col){draw(sphere,x,y,z,a,b,c,col,0,0,0,0);}
    private void box(float x,float y,float z,float a,float b,float c,int col,int tex){draw(cube,x,y,z,a,b,c,col,tex,0,0,0);}
    private void disk(float x,float y,float z,float radius,float height,int col){draw(cylinder,x,y,z,radius,height,radius,col,0,0,0,0);}
    private void room(){
        int style=decor<0?0:decor%4,tint=decor<0?PINK:TINTS[(decor/4)%TINTS.length];
        int wall=style==1?0xFF9997C9:style==2?0xFFB9D9DF:style==3?0xFFBACBB2:0xFFF1DDCB;
        box(0,-.16f,0,3.2f,.16f,2.7f,WOOD,1);
        for(int i=0;i<12;i++)box(-2.93f+i*.53f,.012f,0,.258f,.015f,2.6f,i%2==0?0xFFD7B08B:0xFFCDA47F,1);
        box(0,1.6f,-2.7f,3.2f,1.6f,.09f,wall,2);box(-3.2f,1.6f,0,.09f,1.6f,2.7f,wall,2);
        box(0,.15f,-2.58f,3.15f,.13f,.05f,WHITE,1);box(-3.09f,.15f,0,.05f,.13f,2.6f,WHITE,1);
        box(-.8f,2.05f,-2.56f,1,.76f,.07f,WOOD,1);box(-.8f,2.05f,-2.46f,.87f,.63f,.03f,style==1?0xFF4D6388:0xFFA4DCE7,0);ball(-.38f,2.32f,-2.38f,.17f,.17f,.025f,0xFFFFDF91);
        box(-.8f,2.05f,-2.35f,.035f,.65f,.035f,WHITE,0);box(-.8f,2.05f,-2.35f,.88f,.035f,.035f,WHITE,0);
        for(int side=-1;side<=1;side+=2)for(int fold=0;fold<4;fold++)draw(cylinder,-.8f+side*.97f+fold*.055f,2.05f,-2.27f,.065f,.84f,.07f,tint,2,0,0,0);
        box(-.72f,1.32f,-2.26f,1.2f,.055f,.19f,WHITE,1);
        draw(sphere,.35f,.055f,.55f,1.42f,.035f,1.25f,tint,2,0,0,0);
        for(int i=0;i<28;i++){float a=i*6.28318f/28;ball(.35f+(float)Math.cos(a)*1.32f,.09f,.55f+(float)Math.sin(a)*1.15f,.025f,.01f,.04f,CREAM);}
        // Dining furniture: tablecloth, plate, food, cutlery, water glass, chair.
        for(int dx=-1;dx<=1;dx+=2)for(int dz=-1;dz<=1;dz+=2)box(-1.15f+dx*.57f,.45f,.1f+dz*.43f,.065f,.45f,.065f,WOOD,1);
        box(-1.15f,.94f,.1f,.82f,.08f,.62f,0xFFD7AA78,1);box(-1.15f,1.037f,.1f,.53f,.015f,.49f,CREAM,2);
        box(-1.15f,.47f,-.85f,.44f,.07f,.38f,WOOD,1);box(-1.15f,.97f,-1.25f,.44f,.48f,.045f,WOOD,1);
        for(int dx=-1;dx<=1;dx+=2)for(int dz=-1;dz<=1;dz+=2)box(-1.15f+dx*.32f,.23f,-.85f+dz*.28f,.05f,.23f,.05f,WOOD,1);
        disk(-1.25f,1.07f,.17f,.34f,.025f,WHITE);disk(-1.25f,1.1f,.17f,.27f,.008f,0xFFD2DDD9);
        for(int i=0;i<3;i++)ball(-1.4f+i*.15f,1.16f,.17f,.07f,.05f,.08f,i==0?0xFF82AC65:i==1?0xFFE7A068:0xFFDEC079);
        box(-1.78f,1.09f,.15f,.026f,.015f,.26f,0xFFB2BAC5,0);box(-.78f,1.09f,.15f,.024f,.015f,.25f,0xFFB2BAC5,0);
        for(int i=0;i<3;i++)box(-1.81f+i*.03f,1.09f,-.14f,.009f,.015f,.08f,0xFFB2BAC5,0);glass(-.64f,1.16f,-.27f);
        // Quilted bed and woven pillow.
        box(1.73f,.29f,-1.35f,.84f,.25f,.99f,WOOD,1);box(1.73f,.79f,-2.25f,.86f,.59f,.07f,WOOD,1);
        draw(sphere,1.73f,.58f,-1.35f,.84f,.17f,.96f,CREAM,2,0,0,0);draw(sphere,1.73f,.73f,-1.0f,.85f,.14f,.62f,tint,2,0,0,0);
        for(int i=-2;i<=2;i++)box(1.73f+i*.27f,.84f,-1,.008f,.008f,.49f,CREAM,0);
        draw(sphere,1.73f,.77f,-1.95f,.55f,.16f,.29f,WHITE,2,0,0,0);
        box(-2.7f,.52f,-1.48f,.33f,.5f,.6f,0xFF86A59D,1);ball(-2.7f,1.13f,-1.48f,.2f,.21f,.2f,0xFFD79C7C);
        for(int i=0;i<6;i++){float a=i*1.0472f;draw(sphere,-2.7f+(float)Math.cos(a)*.2f,1.5f+i*.045f,-1.48f+(float)Math.sin(a)*.2f,.13f,.3f,.07f,0xFF73A47D,0,0,i*60,30);}
        box(1.85f,2.5f,-2.3f,.88f,.06f,.26f,WOOD,1);for(int i=0;i<4;i++)box(1.2f+i*.18f,2.76f,-2.34f,.06f,.2f,.13f,TINTS[i],2);
        if(decor>=0)for(int i=0;i<5;i++){
            float x=.55f+i*.43f;
            if(style==0)flower(x,.22f,2.15f,tint);else if(style==1)star(x,2.95f,-2.35f,.12f,0xFFFFD784);
            else if(style==2){ball(x,.14f,2.18f,.18f,.08f,.13f,0xFFF5D1B5);ball(x,.18f,2.18f,.08f,.06f,.08f,WHITE);}else flower(x,.25f,2.14f,0xFF8FAF8A);
        }
    }
    private void glass(float x,float y,float z){disk(x,y,z,.105f,.15f,0xFFC4E5E7);disk(x,y+.14f,z,.087f,.012f,0xFF78C3D5);disk(x,y-.155f,z,.12f,.018f,WHITE);}
    private void flower(float x,float y,float z,int color){disk(x,y-.06f,z,.12f,.13f,0xFFD2A17E);for(int j=0;j<5;j++){float a=j*1.2566f;ball(x+(float)Math.cos(a)*.1f,y+.21f+(float)Math.sin(a)*.1f,z,.065f,.065f,.045f,color);}ball(x,y+.21f,z+.02f,.06f,.06f,.04f,0xFFFFD77D);}
    private void star(float x,float y,float z,float r,int color){for(int i=0;i<5;i++)draw(sphere,x,y,z,r*.26f,r,r*.26f,color,0,0,0,i*72);}
    private void part(float x,float y,float z,float a,float b,float c,int color){float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);draw(sphere,petX+x*cs+z*sn,baseY+y*breath,petZ-x*sn+z*cs,a,b*breath,c,color,material,0,facing,0);}
    private void animal(int state){
        boolean seated=state==1||state==2,sleep=state==5;angle=(float)Math.toRadians(facing);breath=1+(motion?(float)Math.sin(time*2)*.014f:0);baseY=sleep?.69f:seated?.43f:0;
        int fur=FUR[species],belly=species==4?0xFF644337:CREAM;
        if(sleep){
            part(0,.32f,0,.52f,.33f,.65f,fur);part(0,.48f,.47f,.47f,.4f,.42f,fur);
            if(species==5){for(int side=-1;side<=1;side+=2)for(int i=0;i<3;i++)part(side*.47f,.38f+i*.12f,.45f,.16f,.04f,.05f,0xFFD976A1);}
            else{part(-.31f,.75f,.45f,.16f,species==6?.3f:.12f,.13f,fur);part(.31f,.75f,.45f,.16f,species==6?.3f:.12f,.13f,fur);}
            if(species==0){part(-.2f,.9f,.4f,.08f,.17f,.08f,CREAM);part(.2f,.9f,.4f,.08f,.17f,.08f,CREAM);}
            part(0,.35f,.83f,.2f,.1f,.08f,species==1?PINK:CREAM);
            part(-.18f,.5f,.82f,.09f,.018f,.03f,DARK);part(.18f,.5f,.82f,.09f,.018f,.03f,DARK);
            star(petX+.65f,1.7f+(float)Math.sin(time)*.06f,petZ,.09f,0xFFB2A0D2);return;
        }
        float walk=seated?0:(motion?(float)Math.sin(time*4)*.07f:0);
        part(-.25f,.16f+walk,.08f,.22f,.15f,.28f,fur);part(.25f,.16f-walk,.08f,.22f,.15f,.28f,fur);part(0,.73f,0,.48f,.61f,.36f,fur);part(0,.72f,.28f,.33f,.43f,.12f,belly);
        if(species==0){part(0,.4f,-.48f,.18f,.18f,.48f,fur);part(-.49f,.97f,-.17f,.29f,.36f,.07f,0xFFC4B0E4);part(.49f,.97f,-.17f,.29f,.36f,.07f,0xFFC4B0E4);}
        if(species==2)part(0,.28f,-.52f,.24f,.15f,.52f,fur);
        if(species==3||species==4){part(.45f,.4f,-.34f,.22f,.2f,.6f,fur);if(species==4)for(int i=0;i<3;i++)part(.45f,.42f,-.12f-i*.28f,.235f,.21f,.075f,0xFFEDD7B3);}
        if(species==7)for(int i=0;i<10;i++){float a=i*.628f;part((float)Math.cos(a)*.44f,.95f+(float)Math.sin(a)*.38f,-.25f,.16f,.24f,.17f,0xFF795D4A);}
        part(0,1.45f,.04f,.57f,.53f,.48f,fur);
        if(species==6){part(-.29f,2.08f,.02f,.16f,.49f,.13f,fur);part(.29f,2.08f,.02f,.16f,.49f,.13f,fur);part(-.29f,2.1f,.13f,.075f,.35f,.035f,PINK);part(.29f,2.1f,.13f,.075f,.35f,.035f,PINK);}
        else if(species==5){for(int s=-1;s<=1;s+=2)for(int i=0;i<3;i++)part(s*(.53f+i*.035f),1.18f+i*.22f,.01f,.24f,.07f,.07f,0xFFD976A1);}
        else{float ear=species==3||species==4?.24f:.17f;part(-.42f,1.87f,.02f,.2f,ear,.14f,fur);part(.42f,1.87f,.02f,.2f,ear,.14f,fur);part(-.42f,1.88f,.145f,.1f,ear*.6f,.025f,PINK);part(.42f,1.88f,.145f,.1f,ear*.6f,.025f,PINK);}
        if(species==0){part(-.28f,2,-.06f,.1f,.22f,.1f,CREAM);part(.28f,2,-.06f,.1f,.22f,.1f,CREAM);}
        if(species==4){part(-.29f,1.43f,.43f,.23f,.22f,.07f,CREAM);part(.29f,1.43f,.43f,.23f,.22f,.07f,CREAM);}
        if(species==1){part(0,1.24f,.51f,.27f,.18f,.12f,PINK);part(-.09f,1.26f,.63f,.032f,.043f,.016f,0xFFB76D89);part(.09f,1.26f,.63f,.032f,.043f,.016f,0xFFB76D89);}
        else{part(0,1.17f,.45f,.3f,.18f,.12f,belly);part(0,1.26f,.57f,.07f,.05f,.045f,DARK);}
        float blink=motion&&time%5.2f>4.98f?.02f:.105f;
        part(-.22f,1.49f,.47f,.074f,blink,.045f,DARK);part(.22f,1.49f,.47f,.074f,blink,.045f,DARK);
        if(blink>.03f){part(-.24f,1.53f,.505f,.022f,.028f,.012f,WHITE);part(.2f,1.53f,.505f,.022f,.028f,.012f,WHITE);}
        part(-.36f,1.29f,.43f,.09f,.04f,.025f,PINK);part(.36f,1.29f,.43f,.09f,.04f,.025f,PINK);
        if(state==3){part(-.23f,1.66f,.47f,.11f,.025f,.025f,DARK);part(.23f,1.66f,.47f,.11f,.025f,.025f,DARK);part(0,1.08f,.57f,.08f,.018f,.015f,DARK);}else part(0,1.08f,.56f,.065f,.035f,.015f,0xFFB96982);
        float hand=seated?(float)Math.sin(time*3)*.1f:state==4?(float)Math.sin(time*5)*.2f:0;
        part(-.49f,.85f+hand,.16f,.15f,.3f,.16f,fur);part(.49f,.85f-hand,.16f,.15f,.3f,.16f,fur);
        if(state==1){part(-.46f,1.15f+hand,.48f,.025f,.19f,.025f,0xFFB8C4D1);for(int i=0;i<3;i++)part(-.49f+i*.03f,1.35f+hand,.48f,.009f,.07f,.012f,0xFFB8C4D1);part(-.46f,1.42f+hand,.48f,.06f,.04f,.05f,0xFFDEAB7B);}
        if(state==2){float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);glass(petX+.42f*cs+.42f*sn,baseY+1.12f-hand,petZ-.42f*sn+.42f*cs);}
        if(state==4)for(int i=0;i<4;i++)star(petX-.75f+i*.5f,2.45f+(float)Math.sin(time*2+i)*.12f,petZ,.085f,TINTS[i]);
        if(accessory>=0){material=accessory%6==0||accessory%6==3||accessory%6==5?2:0;wear(accessory);material=0;}
    }
    private void wear(int item){int color=TINTS[(item/6)%TINTS.length];switch(item%6){
        case 0:part(-.13f,1.02f,.43f,.15f,.09f,.06f,color);part(.13f,1.02f,.43f,.15f,.09f,.06f,color);part(0,1.02f,.49f,.06f,.06f,.04f,CREAM);break;
        case 1:for(int i=0;i<5;i++)part(-.28f+i*.14f,1.95f+(i%2)*.07f,.1f,.08f,.16f,.08f,0xFFECC46C);part(0,1.88f,.1f,.37f,.07f,.27f,0xFFECC46C);break;
        case 2:for(int s=-1;s<=1;s+=2){part(s*.22f,1.49f,.52f,.16f,.15f,.03f,color);part(s*.22f,1.49f,.55f,.12f,.11f,.025f,0xFFA8D9E3);}part(0,1.49f,.55f,.08f,.025f,.025f,color);break;
        case 3:part(0,1.02f,.25f,.43f,.1f,.24f,color);part(.26f,.8f,.43f,.1f,.28f,.04f,color);for(int i=0;i<4;i++)part(.26f,.58f+i*.045f,.47f,.08f,.009f,.01f,CREAM);break;
        case 4:for(int i=0;i<5;i++){float a=i*1.2566f;part(.45f+(float)Math.cos(a)*.1f,1.83f+(float)Math.sin(a)*.1f,.3f,.075f,.075f,.04f,color);}part(.45f,1.83f,.35f,.055f,.055f,.035f,0xFFFFD87F);break;
        default:part(0,1.95f,.02f,.46f,.07f,.38f,color);part(0,2.1f,.02f,.29f,.23f,.26f,color);part(0,1.98f,.28f,.28f,.045f,.025f,CREAM);
    }}
    private float[] project(float x,float y,float z){float[] r=new float[4];Matrix.multiplyMV(r,0,vp,0,new float[]{x,y,z,1},0);return new float[]{.5f+r[0]/r[3]*.5f,.5f-r[1]/r[3]*.5f};}
    private void setHotspots(){float[] a=project(-1.4f,1.05f,.15f),b=project(-.64f,1.25f,-.27f),c=project(1.73f,.75f,-1.3f),d=project(petX,1.2f,petZ);hotspots=new float[]{a[0],a[1],b[0],b[1],c[0],c[1],d[0],d[1]};}
    public String hit(float x,float y){float[] h=hotspots;String[] names={"eat","drink","sleep","cuddle"};float nearest=.15f;int found=-1;for(int i=0;i<4;i++){float distance=(float)Math.hypot(x-h[i*2],y-h[i*2+1]);if(distance<nearest){nearest=distance;found=i;}}return found<0?"explore":names[found];}
    private static final class Mesh {
        final FloatBuffer data;final int count;
        Mesh(float[] vertices){count=vertices.length/6;data=ByteBuffer.allocateDirect(vertices.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();data.put(vertices).position(0);}
        static void vertex(java.util.ArrayList<Float> out,float x,float y,float z,float nx,float ny,float nz){out.add(x);out.add(y);out.add(z);out.add(nx);out.add(ny);out.add(nz);}
        static Mesh from(java.util.ArrayList<Float> list){float[] v=new float[list.size()];for(int i=0;i<v.length;i++)v[i]=list.get(i);return new Mesh(v);}
        static Mesh sphere(int slices,int stacks){java.util.ArrayList<Float> a=new java.util.ArrayList<>();for(int j=0;j<stacks;j++)for(int i=0;i<slices;i++){int[] dx={0,1,1,0,1,0},dy={0,0,1,0,1,1};for(int k=0;k<6;k++){double u=(i+dx[k])*Math.PI*2/slices,v=(j+dy[k])*Math.PI/stacks;float x=(float)(Math.sin(v)*Math.cos(u)),y=(float)Math.cos(v),z=(float)(Math.sin(v)*Math.sin(u));vertex(a,x,y,z,x,y,z);}}return from(a);}
        static Mesh cube(){java.util.ArrayList<Float> a=new java.util.ArrayList<>();for(int axis=0;axis<3;axis++)for(int side=-1;side<=1;side+=2){int[] u={-1,1,1,-1,1,-1},v={-1,-1,1,-1,1,1};for(int k=0;k<6;k++){float[] p=new float[3],n=new float[3];p[axis]=side;p[(axis+1)%3]=u[k];p[(axis+2)%3]=v[k];n[axis]=side;vertex(a,p[0],p[1],p[2],n[0],n[1],n[2]);}}return from(a);}
        static Mesh cylinder(int slices){java.util.ArrayList<Float> a=new java.util.ArrayList<>();for(int i=0;i<slices;i++){float x=(float)Math.cos(i*Math.PI*2/slices),z=(float)Math.sin(i*Math.PI*2/slices),xx=(float)Math.cos((i+1)*Math.PI*2/slices),zz=(float)Math.sin((i+1)*Math.PI*2/slices);vertex(a,x,-1,z,x,0,z);vertex(a,xx,-1,zz,xx,0,zz);vertex(a,xx,1,zz,xx,0,zz);vertex(a,x,-1,z,x,0,z);vertex(a,xx,1,zz,xx,0,zz);vertex(a,x,1,z,x,0,z);for(int s=-1;s<=1;s+=2){vertex(a,0,s,0,0,s,0);vertex(a,x,s,z,0,s,0);vertex(a,xx,s,zz,0,s,0);}}return from(a);}
    }
}
