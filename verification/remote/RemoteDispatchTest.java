package com.example.streamwindow;
import java.util.*;
class KeyEvent {
 static final int ACTION_DOWN=0,ACTION_UP=1,KEYCODE_TAB=61,META_SHIFT_ON=1;
 int action,code,repeat,modifiers;
 KeyEvent(long d,long t,int a,int c,int r,int m){action=a;code=c;repeat=r;modifiers=m;}
 int getKeyCode(){return code;} int getAction(){return action;} int getRepeatCount(){return repeat;}
}
class Base { List<KeyEvent> sent=new ArrayList<>(); public boolean dispatchKeyEvent(KeyEvent e){sent.add(e);return false;} }
public class RemoteDispatchTest extends Base {
 boolean television=true; Object fullView=new Object(); long lastTvFocusMove;
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int direction = TvRemotePolicy.focusDirection(event.getKeyCode());
        if(television && fullView!=null && direction!=0) {
            // Consume BOTH halves of the arrow. Providers may seek on keydown or keyup.
            // Tab uses the focused frame's native focus order, including cross-origin players.
            // No script injection into the provider frame and no synthetic OK/click.
            long now = android.os.SystemClock.uptimeMillis();
            if(event.getAction()==KeyEvent.ACTION_DOWN &&
                (event.getRepeatCount()==0 || now-lastTvFocusMove>=180)) {
                lastTvFocusMove=now;
                int modifiers=direction<0?KeyEvent.META_SHIFT_ON:0;
                super.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB,0,modifiers));
                super.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB,0,modifiers));
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

 static int checks;
 static void check(boolean x){checks++;if(!x)throw new AssertionError("Case "+checks);}
 static KeyEvent event(int action,int key,int repeat){return new KeyEvent(1000,1000,action,key,repeat,0);}
 public static void main(String[] args){
  for(int key:new int[]{19,20,21,22}) {
   RemoteDispatchTest t=new RemoteDispatchTest();
   check(t.dispatchKeyEvent(event(0,key,0)));
   check(t.dispatchKeyEvent(event(1,key,0)));
   check(t.sent.size()==2 && t.sent.get(0).code==61 && t.sent.get(1).code==61);
   check(t.sent.get(0).action==0 && t.sent.get(1).action==1);
   check(t.sent.get(0).modifiers==((key==19||key==21)?1:0));
   t.dispatchKeyEvent(event(0,key,1));check(t.sent.size()==2);
  }
  for(int key:new int[]{23,66,4,85,89,90}) {
   RemoteDispatchTest t=new RemoteDispatchTest();KeyEvent e=event(0,key,0);
   t.dispatchKeyEvent(e);check(t.sent.size()==1&&t.sent.get(0)==e);
  }
  RemoteDispatchTest t=new RemoteDispatchTest();t.television=false;KeyEvent e=event(0,20,0);
  t.dispatchKeyEvent(e);check(t.sent.get(0)==e);
  t=new RemoteDispatchTest();t.fullView=null;t.dispatchKeyEvent(e);check(t.sent.get(0)==e);
  System.out.println(checks+" fullscreen remote dispatch checks passed (stub Android event sink).");
 }
}
