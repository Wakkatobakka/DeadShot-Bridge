package com.wakka.omnideadshot;
import android.content.Context;
import com.wakka.bridge.BridgeBackend;
import com.wakka.deadshot.DeadshotBackend;
public final class DeadshotCatalog {
    private static BridgeBackend game;
    private DeadshotCatalog() {}
    public static synchronized BridgeBackend game(Context c) { if(game==null) game=new DeadshotBackend(c);return game; }
}
