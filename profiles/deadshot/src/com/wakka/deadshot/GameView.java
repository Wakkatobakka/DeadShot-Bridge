package com.wakka.deadshot;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Field;
import java.util.HashMap;
import com.nttdocomo.ui.Frame;

public class GameView extends View {
    private static final int KEY_NUM0=0, KEY_NUM1=1, KEY_NUM2=2, KEY_NUM3=3, KEY_NUM4=4;
    private static final int KEY_NUM5=5, KEY_NUM6=6, KEY_NUM7=7, KEY_NUM8=8, KEY_NUM9=9;
    private static final int KEY_STAR=10, KEY_POUND=11;
    private static final int KEY_LEFT=16, KEY_UP=17, KEY_RIGHT=18, KEY_DOWN=19;
    private static final int KEY_SELECT=20, KEY_SOFT1=21, KEY_SOFT2=22;

    // Keep DeadShot's v0.0.5 verified native key codes.  The Dirge Bridge
    // contribution here is the contact router, not Dirge's game-specific key map.
    private static final int AREA_NONE=0, AREA_STAR=1, AREA_OK=2, AREA_POUND=3;
    private static final int AREA_SOFT1=4, AREA_SOFT2=5, AREA_NAV=6, AREA_ACTION=7;
    private static final int AREA_KEYPAD_BASE=100;
    private static final float TOUCH_SLOP_DP=4f;

    // Omni palette retained; control geometry and semantics below are DeadShot-specific.
    private static final int PAD_BG=0xFF182333;
    private static final int BOX=0xFF202C3E;
    private static final int BOX_ALT=0xFF602B40;
    private static final int BOX_DOWN=0xFF9D4464;
    private static final int STROKE=0xFF44536A;
    private static final int STROKE_DOWN=0xFFE396B1;
    private static final int TEXT=0xFFF6F7FA;
    private static final int HINT=0xFFD7C9CF;
    private static final int NAV_TEXT=0xFFACBBCE;
    private static final int CENTER_DOT=0xFF64758A;

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

    // Sticky pointer capture: every finger remembers the control it began on.
    // Button contacts stay sticky to that control; navigation contacts may change
    // direction inside the navigation surface.  This prevents a tiny finger drift
    // from silently turning one DeadShot key into another key.
    private static final class Contact {
        final int area;
        int key;
        Contact(int area,int key){ this.area=area; this.key=key; }
    }
    private final HashMap<Integer,Contact> contacts=new HashMap<>();
    private Throwable fatal;

    private static Field keyConField;
    private static boolean keyConLookupTried;

    private final RectF starRect=new RectF();
    private final RectF selectShortcutRect=new RectF();
    private final RectF poundRect=new RectF();
    private final RectF menuRect=new RectF();
    private final RectF backRect=new RectF();
    private final RectF modeRect=new RectF();
    private final RectF navRect=new RectF();
    private final RectF actionRect=new RectF();

    // The Omni toolbar toggles this DeadShot-specific deck and the complete
    // original phone keypad in-place. Keep the full keypad unchanged.
    private boolean keypadMode;
    private final RectF[] keypadRects = new RectF[19];
    private final int[] keypadKeys = {
            KEY_SOFT1, KEY_UP, KEY_SOFT2,
            KEY_LEFT, KEY_SELECT, KEY_RIGHT,
            KEY_DOWN,
            KEY_NUM1, KEY_NUM2, KEY_NUM3,
            KEY_NUM4, KEY_NUM5, KEY_NUM6,
            KEY_NUM7, KEY_NUM8, KEY_NUM9,
            KEY_STAR, KEY_NUM0, KEY_POUND
    };
    private final String[] keypadLabels = {
            "L soft", "▲", "R soft",
            "◀", "OK", "▶",
            "▼",
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "*", "0", "#"
    };

    public GameView(Context c){
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        for(int i=0;i<keypadRects.length;i++) keypadRects[i]=new RectF();
    }
    public void setFatal(Throwable t){ fatal=t; postInvalidate(); }
    public boolean isKeypadMode(){ return keypadMode; }
    public void setKeypadMode(boolean enabled){
        if(keypadMode==enabled) return;
        releaseAllTouches();
        keypadMode=enabled;
        requestLayout();
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(android.graphics.Canvas c){
        super.onDraw(c);
        int w=getWidth(),h=getHeight();
        if(keypadMode){
            layoutKeypad(w,h);
            drawKeypad(c);
        } else {
            layoutControls(w,h);
            drawControls(c);
        }
        if(fatal!=null) drawFatal(c,w);
    }

    /**
     * Keep the controller in its own wrap-content view below the game/status
     * stack instead of positioning controls inside a full-screen canvas.
     */
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        int width=MeasureSpec.getSize(widthMeasureSpec);
        if(width<=0) width=Math.round(dp(360));
        // The shared Omni shell gives the custom controller exactly 244dp.
        // Design this deck for that real canvas instead of drawing a 290dp
        // layout and shrinking every touch target afterward.
        int desired=Math.round(dp(keypadMode ? 420 : 244));
        int height=resolveSize(desired,heightMeasureSpec);
        setMeasuredDimension(width,height);
    }

    private void layoutControls(int w,int h){
        float density=getResources().getDisplayMetrics().density;
        float scale=Math.min(density,Math.max(0.1f,h/244f));
        float logicalW=w/scale;
        float margin=7f, gap=8f;

        // DeadShot owns this deck.  Only expose native soft keys when the game
        // itself currently advertises a label; do not invent MENU/BACK fallbacks.
        String leftSoft=softLabel(0), rightSoft=softLabel(1);
        float topY=4f, topH=50f;
        float softW=(logicalW-margin*2-gap)/2f;
        menuRect.setEmpty();
        backRect.setEmpty();
        if(leftSoft!=null) setScaled(menuRect,margin,topY,softW,topH,scale,0);
        if(rightSoft!=null) setScaled(backRect,logicalW-margin-softW,topY,softW,topH,scale,0);

        // The custom deck does not expose literal phone *, # or OK buttons.
        // Instead one large right-thumb surface routes to DeadShot's current
        // primary gameplay key.  Reuse the existing native areas underneath so
        // keyForArea/contact routing and the full keypad remain untouched.
        starRect.setEmpty();
        selectShortcutRect.setEmpty();
        poundRect.setEmpty();
        modeRect.setEmpty();

        float navSize=Math.min(164f,Math.max(154f,logicalW*0.455f));
        float navY=78f;
        setScaled(navRect,margin,navY,navSize,navSize,scale,0);

        float actionSize=130f;
        float actionRightInset=12f;
        float actionX=logicalW-actionRightInset-actionSize;
        float actionY=96f;
        setScaled(actionRect,actionX,actionY,actionSize,actionSize,scale,0);
        if(primaryActionKey()==KEY_SELECT) selectShortcutRect.set(actionRect);
        else poundRect.set(actionRect);
    }

    private void layoutKeypad(int w,int h){
        float density=getResources().getDisplayMetrics().density;
        float logicalH=420f;
        float scale=Math.min(density,Math.max(0.1f,h/logicalH));
        float logicalW=w/scale;
        float margin=7f, gap=5f;
        float cellW=(logicalW-margin*2-gap*2)/3f;
        float cellH=48f;
        float y=7f;

        // Original phone navigation block. This full keypad is the unchanged baseline.
        setScaled(keypadRects[0],margin,y,cellW,cellH,scale,0);
        setScaled(keypadRects[1],margin+cellW+gap,y,cellW,cellH,scale,0);
        setScaled(keypadRects[2],margin+(cellW+gap)*2,y,cellW,cellH,scale,0);
        y+=cellH+gap;
        setScaled(keypadRects[3],margin,y,cellW,cellH,scale,0);
        setScaled(keypadRects[4],margin+cellW+gap,y,cellW,cellH,scale,0);
        setScaled(keypadRects[5],margin+(cellW+gap)*2,y,cellW,cellH,scale,0);
        y+=cellH+gap;
        setScaled(keypadRects[6],margin+cellW+gap,y,cellW,cellH,scale,0);

        // 1-9, *, 0, #.
        y+=cellH+10f;
        int idx=7;
        for(int row=0;row<4;row++){
            for(int col=0;col<3;col++){
                setScaled(keypadRects[idx++],margin+col*(cellW+gap),y,cellW,cellH,scale,0);
            }
            y+=cellH+gap;
        }
    }

    private void drawKeypad(android.graphics.Canvas c){
        p.setTypeface(Typeface.DEFAULT_BOLD);
        for(int i=0;i<keypadRects.length;i++){
            RectF r=keypadRects[i];
            int key=keypadKeys[i];
            boolean down=isKeyPressed(key);
            boolean ok=(key==KEY_SELECT);
            p.setStyle(Paint.Style.FILL);
            p.setColor(down?BOX_DOWN:(ok?BOX_ALT:BOX));
            c.drawRoundRect(r,dp(7),dp(7),p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(down?STROKE_DOWN:STROKE);
            c.drawRoundRect(r,dp(7),dp(7),p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setColor(TEXT);
            p.setTextSize(key>=KEY_SOFT1 ? dp(17) : dp(22));
            drawCentered(c,keypadLabels[i],r.centerX(),r.centerY());
        }
    }

    private void setScaled(RectF r,float x,float y,float w,float h,float s,float top){
        r.set(x*s,top+y*s,(x+w)*s,top+(y+h)*s);
    }

    private void drawControls(android.graphics.Canvas c){
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));
        String leftSoft=softLabel(0), rightSoft=softLabel(1);
        if(leftSoft!=null&&!menuRect.isEmpty()) drawDirgeBox(c,menuRect,KEY_SOFT1,leftSoft,"SOFT1",false);
        if(rightSoft!=null&&!backRect.isEmpty()) drawDirgeBox(c,backRect,KEY_SOFT2,rightSoft,"SOFT2",false);
        drawNav(c);
        int actionKey=primaryActionKey();
        drawDirgeBox(c,actionRect,actionKey,primaryActionTitle(),actionKey==KEY_POUND?"#":"OK",true);
    }

    private void drawDirgeBox(android.graphics.Canvas c,RectF r,int key,String title,String hint,boolean oval){
        boolean down=isKeyPressed(key);
        p.setStyle(Paint.Style.FILL);
        p.setColor(down?BOX_DOWN:(oval?BOX_ALT:BOX));
        if(oval) c.drawOval(r,p); else c.drawRoundRect(r,dp(11),dp(11),p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(down?STROKE_DOWN:STROKE);
        if(oval) c.drawOval(r,p); else c.drawRoundRect(r,dp(11),dp(11),p);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(TEXT); p.setTextSize(Math.min(dp(23),r.height()*0.25f));
        drawCentered(c,title,r.centerX(),r.centerY()-r.height()*0.09f);
        p.setTypeface(Typeface.DEFAULT); p.setColor(HINT); p.setTextSize(Math.min(dp(12),r.height()*0.13f));
        drawCentered(c,hint,r.centerX(),r.centerY()+r.height()*0.19f);
    }

    private void drawModeStrip(android.graphics.Canvas c){
        p.setStyle(Paint.Style.FILL); p.setColor(PAD_BG); c.drawRoundRect(modeRect,dp(11),dp(11),p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(STROKE); c.drawRoundRect(modeRect,dp(11),dp(11),p);
        float mid=modeRect.centerX();
        p.setColor(0x8844536A); c.drawLine(mid,modeRect.top+dp(8),mid,modeRect.bottom-dp(8),p);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(TEXT); p.setTextSize(dp(15));
        drawCentered(c,"CONTROL",modeRect.left+modeRect.width()*0.28f,modeRect.centerY()-dp(5));
        p.setColor(HINT); p.setTextSize(dp(10)); p.setTypeface(Typeface.DEFAULT);
        drawCentered(c,"mode",modeRect.left+modeRect.width()*0.28f,modeRect.centerY()+dp(11));
        p.setColor(TEXT); p.setTextSize(dp(14)); p.setTypeface(Typeface.DEFAULT_BOLD);
        drawCentered(c,numericMovementMode()?"KEYPAD":"D-PAD",modeRect.left+modeRect.width()*0.75f,modeRect.centerY());
    }

    private void drawNav(android.graphics.Canvas c){
        boolean pressed=isDirectionalPressed();
        p.setStyle(Paint.Style.FILL); p.setColor(PAD_BG); c.drawRoundRect(navRect,dp(22),dp(22),p);

        p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(NAV_TEXT); p.setTextSize(dp(12));
        String mode=numericMovementMode()?"MOVE · 8-WAY":"MOVE · 4-WAY";
        drawCentered(c,mode,navRect.centerX(),navRect.top-dp(10));

        float s=navRect.width();
        float tile=s*0.39f;
        float left=navRect.left+s*0.305f;
        float top=navRect.top+s*0.015f;
        RectF up=new RectF(left,top,left+tile,top+tile);
        RectF leftR=new RectF(navRect.left+s*0.015f,navRect.top+s*0.305f,navRect.left+s*0.015f+tile,navRect.top+s*0.305f+tile);
        RectF rightR=new RectF(navRect.left+s*0.595f,navRect.top+s*0.305f,navRect.left+s*0.595f+tile,navRect.top+s*0.305f+tile);
        RectF down=new RectF(left,navRect.top+s*0.595f,left+tile,navRect.top+s*0.595f+tile);
        drawDirTile(c,up,pressedKeyForDirection(KEY_UP,KEY_NUM2),"▲");
        drawDirTile(c,leftR,pressedKeyForDirection(KEY_LEFT,KEY_NUM4),"◀");
        drawDirTile(c,rightR,pressedKeyForDirection(KEY_RIGHT,KEY_NUM6),"▶");
        drawDirTile(c,down,pressedKeyForDirection(KEY_DOWN,KEY_NUM8),"▼");
        p.setStyle(Paint.Style.FILL); p.setColor(CENTER_DOT); c.drawCircle(navRect.centerX(),navRect.centerY(),dp(3),p);
    }

    private boolean pressedKeyForDirection(int dpad,int num){ return isKeyPressed(numericMovementMode()?num:dpad); }

    private void drawDirTile(android.graphics.Canvas c,RectF r,boolean down,String glyph){
        p.setStyle(Paint.Style.FILL); p.setColor(down?BOX_DOWN:BOX); c.drawRoundRect(r,dp(11),dp(11),p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(down?STROKE_DOWN:STROKE); c.drawRoundRect(r,dp(11),dp(11),p);
        p.setStyle(Paint.Style.FILL); p.setColor(TEXT); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(dp(23)); p.setTypeface(Typeface.DEFAULT_BOLD);
        drawCentered(c,glyph,r.centerX(),r.centerY());
    }

    private String softLabel(int index){
        Frame f=Host.currentFrame();
        if(f!=null) try{
            String s=f.__softLabel(index);
            if(s!=null){ s=s.trim(); if(!s.isEmpty()&&s.length()<=12&&mostlyPrintable(s)) return s; }
        }catch(Throwable ignored){}
        return null;
    }

    private int primaryActionKey(){
        int mode=controlModeValue();
        return (mode==1||mode==3)?KEY_SELECT:KEY_POUND;
    }

    private String primaryActionTitle(){
        return controlModeValue()>=2?"DIR LOCK":"ATTACK";
    }

    private int controlModeValue(){
        try{
            if(!keyConLookupTried){ keyConLookupTried=true; Class<?> c=PayloadStore.gameClass(getContext(),"InitAll"); keyConField=c.getDeclaredField("key_con"); keyConField.setAccessible(true); }
            if(keyConField!=null) return keyConField.getInt(null);
        }catch(Throwable ignored){}
        // DeadShot initializes key_con to type 1 (zero-based 0): manual attack,
        // directional movement, #/* action.  Use that as the safe pre-boot view.
        return 0;
    }

    private static boolean mostlyPrintable(String s){ for(int i=0;i<s.length();i++) if(Character.isISOControl(s.charAt(i))) return false; return true; }
    private void drawCentered(android.graphics.Canvas c,String s,float x,float y){ Paint.FontMetrics fm=p.getFontMetrics(); c.drawText(s,x,y-(fm.ascent+fm.descent)/2f,p); }
    private float dp(float v){ return v*getResources().getDisplayMetrics().density; }

    private int hitAreaAt(float x,float y){
        if(keypadMode){
            for(int i=0;i<keypadRects.length;i++) if(keypadRects[i].contains(x,y)) return AREA_KEYPAD_BASE+i;
            return AREA_NONE;
        }
        if(starRect.contains(x,y)) return AREA_STAR;
        if(selectShortcutRect.contains(x,y)) return AREA_OK;
        if(poundRect.contains(x,y)) return AREA_POUND;
        if(menuRect.contains(x,y)) return AREA_SOFT1;
        if(backRect.contains(x,y)) return AREA_SOFT2;
        if(actionRect.contains(x,y)) return AREA_ACTION;
        if(navRect.contains(x,y)) return AREA_NAV;
        return AREA_NONE;
    }

    private int keyForArea(int area,float x,float y){
        if(area>=AREA_KEYPAD_BASE && area<AREA_KEYPAD_BASE+keypadKeys.length) return keypadKeys[area-AREA_KEYPAD_BASE];
        switch(area){
            case AREA_STAR:return KEY_STAR;
            case AREA_OK:return KEY_SELECT;
            case AREA_POUND:return KEY_POUND;
            case AREA_SOFT1:return KEY_SOFT1;
            case AREA_SOFT2:return KEY_SOFT2;
            case AREA_ACTION:return KEY_SELECT;
            case AREA_NAV:return directionKey(x-navRect.centerX(),y-navRect.centerY());
            default:return -1;
        }
    }

    private RectF rectForArea(int area){
        if(area>=AREA_KEYPAD_BASE && area<AREA_KEYPAD_BASE+keypadRects.length) return keypadRects[area-AREA_KEYPAD_BASE];
        switch(area){
            case AREA_STAR:return starRect;
            case AREA_OK:return selectShortcutRect;
            case AREA_POUND:return poundRect;
            case AREA_SOFT1:return menuRect;
            case AREA_SOFT2:return backRect;
            case AREA_NAV:return navRect;
            case AREA_ACTION:return actionRect;
            default:return null;
        }
    }

    private boolean containsWithSlop(RectF r,float x,float y){
        if(r==null) return false;
        float slop=dp(TOUCH_SLOP_DP);
        return x>=r.left-slop && x<=r.right+slop && y>=r.top-slop && y<=r.bottom+slop;
    }

    private int directionKey(float dx,float dy){
        if(!numericMovementMode()){
            if(Math.abs(dx)>Math.abs(dy)) return dx<0?KEY_LEFT:KEY_RIGHT;
            return dy<0?KEY_UP:KEY_DOWN;
        }
        double a=Math.atan2(dy,dx);
        int sector=(int)Math.floor((a+Math.PI/8.0)/(Math.PI/4.0)); sector=((sector%8)+8)%8;
        switch(sector){
            case 0:return KEY_NUM6; case 1:return KEY_NUM9; case 2:return KEY_NUM8; case 3:return KEY_NUM7;
            case 4:return KEY_NUM4; case 5:return KEY_NUM1; case 6:return KEY_NUM2; case 7:return KEY_NUM3; default:return KEY_NUM2;
        }
    }

    private boolean numericMovementMode(){
        try{
            if(!keyConLookupTried){ keyConLookupTried=true; Class<?> c=PayloadStore.gameClass(getContext(),"InitAll"); keyConField=c.getDeclaredField("key_con"); keyConField.setAccessible(true); }
            if(keyConField!=null){ int v=keyConField.getInt(null); return v==1||v==3; }
        }catch(Throwable ignored){}
        return false;
    }

    private int touchMask(){
        int mask=0;
        for(Contact c:contacts.values()) if(c!=null&&c.key>=0&&c.key<31) mask|=(1<<c.key);
        return mask;
    }

    private boolean isKeyPressed(int key){ return key>=0 && (touchMask()&(1<<key))!=0; }
    private boolean isDirectionalPressed(){
        int mask=touchMask();
        int[] keys={KEY_LEFT,KEY_UP,KEY_RIGHT,KEY_DOWN,KEY_NUM1,KEY_NUM2,KEY_NUM3,KEY_NUM4,KEY_NUM6,KEY_NUM7,KEY_NUM8,KEY_NUM9};
        for(int key:keys) if((mask&(1<<key))!=0) return true;
        return false;
    }

    private void capturePointer(int id,float x,float y){
        int area=hitAreaAt(x,y);
        int key=area==AREA_NONE?-1:keyForArea(area,x,y);
        contacts.put(id,new Contact(area,key));
    }

    private void movePointer(int id,float x,float y){
        Contact c=contacts.get(id);
        if(c==null) return;
        if(c.area==AREA_NONE){ c.key=-1; return; }
        RectF r=rectForArea(c.area);
        if(!containsWithSlop(r,x,y)){ c.key=-1; return; }
        // Navigation is the only surface whose logical key is allowed to change
        // during the same contact.  Every other control remains tied to the area
        // captured on ACTION_DOWN, matching the supplied Dirge Bridge behavior.
        c.key=keyForArea(c.area,x,y);
    }

    private void publishTouchDiff(int before){
        int after=touchMask();
        int released=before&~after;
        int pressed=after&~before;
        // Always release old keys before pressing replacements.
        for(int key=0;key<=KEY_SOFT2;key++) if((released&(1<<key))!=0) send(1,key);
        for(int key=0;key<=KEY_SOFT2;key++) if((pressed&(1<<key))!=0) send(0,key);
        postInvalidateOnAnimation();
    }

    private void releaseAllTouches(){
        int before=touchMask();
        contacts.clear();
        publishTouchDiff(before);
    }

    private void send(int type,int key){ Host.routeKey(type,key); }
    public void cancelContacts(){ releaseAllTouches(); }

    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked(),idx=e.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            if(getParent()!=null) getParent().requestDisallowInterceptTouchEvent(true);
            int before=touchMask();
            int id=e.getPointerId(idx);
            capturePointer(id,e.getX(idx),e.getY(idx));
            publishTouchDiff(before);
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            int before=touchMask();
            for(int i=0;i<e.getPointerCount();i++) movePointer(e.getPointerId(i),e.getX(i),e.getY(i));
            publishTouchDiff(before);
            return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){
            int before=touchMask();
            contacts.remove(e.getPointerId(idx));
            publishTouchDiff(before);
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){
            releaseAllTouches();
            return true;
        }
        return true;
    }

    @Override public boolean onKeyDown(int keyCode,KeyEvent e){ if(keyCode!=KeyEvent.KEYCODE_BACK&&Host.routeHardware(keyCode,true))return true;return super.onKeyDown(keyCode,e); }
    @Override public boolean onKeyUp(int keyCode,KeyEvent e){ if(keyCode!=KeyEvent.KEYCODE_BACK&&Host.routeHardware(keyCode,false))return true;return super.onKeyUp(keyCode,e); }
    int mapHardwareKey(int k){
        switch(k){
            case KeyEvent.KEYCODE_DPAD_UP:return numericMovementMode()?KEY_NUM2:KEY_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:return numericMovementMode()?KEY_NUM8:KEY_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:return numericMovementMode()?KEY_NUM4:KEY_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:return numericMovementMode()?KEY_NUM6:KEY_RIGHT;
            case KeyEvent.KEYCODE_0:return KEY_NUM0; case KeyEvent.KEYCODE_1:return KEY_NUM1; case KeyEvent.KEYCODE_2:return KEY_NUM2; case KeyEvent.KEYCODE_3:return KEY_NUM3; case KeyEvent.KEYCODE_4:return KEY_NUM4;
            case KeyEvent.KEYCODE_5:return KEY_NUM5; case KeyEvent.KEYCODE_6:return KEY_NUM6; case KeyEvent.KEYCODE_7:return KEY_NUM7; case KeyEvent.KEYCODE_8:return KEY_NUM8; case KeyEvent.KEYCODE_9:return KEY_NUM9;
            case KeyEvent.KEYCODE_STAR: case KeyEvent.KEYCODE_BUTTON_X:return KEY_STAR;
            case KeyEvent.KEYCODE_POUND: case KeyEvent.KEYCODE_BUTTON_B:return KEY_POUND;
            case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_DPAD_CENTER:return KEY_SELECT;
            case KeyEvent.KEYCODE_BUTTON_START: case KeyEvent.KEYCODE_MENU:return KEY_SOFT1;
            case KeyEvent.KEYCODE_BUTTON_SELECT: case KeyEvent.KEYCODE_BACK:return KEY_SOFT2;
            default:return -1;
        }
    }

    private void drawFatal(android.graphics.Canvas c,int w){
        p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextSize(Math.max(24,w/30f)); p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.DEFAULT);
        float y=60; c.drawText("DeadShot Bridge boot error",30,y,p); y+=40; String s=fatal.toString();
        for(int i=0;i<s.length();i+=44){ c.drawText(s.substring(i,Math.min(s.length(),i+44)),30,y,p); y+=34; }
    }
}
