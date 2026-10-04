package com.wakka.deadshot;
import com.wakka.bridge.InputLatch;
/** Contact ownership plus DeadShot type 0 press / type 1 release transitions. */
public final class NativeEventRouter {
    public interface Sink { void send(int type,int nativeKey); }
    private final InputLatch owners=new InputLatch();
    private final Sink sink;
    private int published;
    public NativeEventRouter(Sink sink) { this.sink=sink; }
    public synchronized void set(int owner,int mask) { publish(owners.set(owner,mask)); }
    public synchronized void remove(int owner) { publish(owners.remove(owner)); }
    public synchronized void clear() { publish(owners.clear()); }
    public synchronized int mask() { return owners.mask(); }
    public synchronized int contacts() { return owners.contacts(); }
    private void publish(int after) {
        int released=published&~after,pressed=after&~published;published=after;
        for(int key=0;key<=22;key++) if((released&(1<<key))!=0) sink.send(1,key);
        for(int key=0;key<=22;key++) if((pressed&(1<<key))!=0) sink.send(0,key);
    }
}
