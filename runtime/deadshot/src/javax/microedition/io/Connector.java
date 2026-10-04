package javax.microedition.io;

import java.io.*;
import com.wakka.deadshot.ScratchpadStore;
import com.nttdocomo.io.HttpConnection;

public final class Connector {
    private Connector(){}
    public static DataInputStream openDataInputStream(String uri) throws IOException { return new DataInputStream(ScratchpadStore.input(parsePos(uri))); }
    public static DataOutputStream openDataOutputStream(String uri) throws IOException { return new DataOutputStream(ScratchpadStore.output(parsePos(uri))); }
    public static OutputStream openOutputStream(String uri) throws IOException { return ScratchpadStore.output(parsePos(uri)); }
    public static Connection open(String uri,int mode,boolean timeouts) throws IOException { return new StubHttp(uri); }
    private static int parsePos(String s){ int i=s==null?-1:s.indexOf("pos="); if(i<0)return 0; int start=i+4,end=start; while(end<s.length()&&Character.isDigit(s.charAt(end)))end++; try{return Integer.parseInt(s.substring(start,end));}catch(Exception e){return 0;} }
    private static final class StubHttp implements HttpConnection {
        private final String uri; StubHttp(String u){uri=u;}
        public void setRequestMethod(String m){}
        public void connect(){}
        public InputStream openInputStream(){ if(uri!=null&&uri.contains("ac_check.php")) return new ByteArrayInputStream(new byte[]{'1'}); return new ByteArrayInputStream(new byte[64]); }
        public void close(){}
    }
}
