package com.nttdocomo.ui;

import com.wakka.deadshot.Host;

public class IApplication {
    private static IApplication current;
    public IApplication(){ current=this; }
    public void start() {}
    public void resume() {}
    public String getSourceURL(){ return "http://deadshot.local/"; }
    public static IApplication getCurrentApp(){ return current; }
    public void terminate(){ Host.terminate(); }
}
