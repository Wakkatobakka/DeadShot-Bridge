package com.wakka.deadshot;

import android.content.Context;
import android.net.Uri;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** App-private, integrity-checked storage for user-supplied DeadShot data. */
public final class PayloadStore {
    public static final String GAME_JAR_SHA256="e2d4dfdcb1fe7274078e5c92ce28a88996ea4e320797a908fa15d8b2500e6c56";
    public static final String GAME_DEX_SHA256="e39d8316234adfb3b9239b37ee6f59dec16c964d27c3889b0b54491bb1ba221e";
    public static final String SP_SHA256="29d89f662798c8ce76d9a7fe4d664eb06a6a17b46886c0b01bd8436d3583c66d";
    private static final String[] MIDI_SHA256={
        "d5531c2d876fe183c20d3b8bb0653577ffb21aca7fe1393a54e253c9ecbe3fa0",
        "6d91dffcaaa096c91fc712a0e42a8730ecb1b22764c863082b8e809749e85ff1",
        "c411f95a00b694c9872d514e081807556dcd33d395de17c0ccd1a3062e807670",
        "8daeed817ba0ff9ff37e382e700f186e4e4d247fe65ad1c2bc8996bdfabf545b"
    };
    private static final String[] REQUIRED={"game.dex","deadshot.sp","deadshot_0.mid","deadshot_1.mid","deadshot_2.mid","deadshot_3.mid"};
    private static final long MAX_ZIP_BYTES=2L*1024L*1024L;
    private static volatile ClassLoader gameLoader;
    private PayloadStore() {}

    private static File dir(Context c){ return new File(c.getFilesDir(),"deadshot-payload"); }
    public static File file(Context c,String name){ return new File(dir(c),name); }

    public static boolean ready(Context c){
        try { verifyInstalled(c); return true; } catch(Exception ignored) { return false; }
    }

    public static String status(Context c){
        try { verifyInstalled(c); return "DeadShot game data imported / verified"; }
        catch(Exception e) { return "Game data required · import your DeadShot payload ZIP"; }
    }

    public static synchronized void importZip(Context context,Uri uri) throws Exception {
        if(uri==null) throw new IOException("No ZIP selected.");
        File base=dir(context), parent=base.getParentFile();
        if(parent==null) throw new IOException("Internal storage unavailable.");
        File tmp=new File(parent,"deadshot-payload-importing");
        deleteTree(tmp); if(!tmp.mkdirs()&&!tmp.isDirectory()) throw new IOException("Could not create import folder.");
        Set<String> found=new HashSet<>(); long total=0;
        try(InputStream raw=context.getContentResolver().openInputStream(uri)) {
            if(raw==null) throw new IOException("Could not open selected ZIP.");
            try(ZipInputStream zin=new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry entry;
                byte[] buf=new byte[16384];
                while((entry=zin.getNextEntry())!=null) {
                    if(entry.isDirectory()) continue;
                    String name=entry.getName().replace('\\','/');
                    if(name.contains("/")||name.contains("..")) continue;
                    if(!isRequired(name)) continue;
                    File out=new File(tmp,name); long size=0;
                    try(FileOutputStream rawOut=new FileOutputStream(out)) {
                        // Android 14+ requires dynamically loaded code to be read-only. Mark the
                        // file read-only after opening the descriptor but before writing bytes,
                        // which avoids a writable-code race while allowing this open descriptor
                        // to finish the import.
                        if("game.dex".equals(name) && !out.setReadOnly())
                            throw new IOException("Could not mark game.dex read-only.");
                        try(OutputStream os=new BufferedOutputStream(rawOut)) {
                            int n; while((n=zin.read(buf))>=0) { if(n==0) continue; size+=n; total+=n; if(total>MAX_ZIP_BYTES) throw new IOException("Payload ZIP is unexpectedly large."); os.write(buf,0,n); }
                        }
                    }
                    found.add(name);
                }
            }
        } catch(Exception e) { deleteTree(tmp); throw e; }
        for(String req:REQUIRED) if(!found.contains(req)) { deleteTree(tmp); throw new IOException("Missing required payload file: "+req); }
        verifyDir(tmp);
        deleteTree(base);
        if(!tmp.renameTo(base)) { copyTree(tmp,base); deleteTree(tmp); }
        File installedDex=file(context,"game.dex");
        if(!installedDex.setReadOnly()) throw new IOException("Could not finalize game.dex as read-only.");
        verifyInstalled(context);
        gameLoader=null;
    }

    public static synchronized ClassLoader gameClassLoader(Context c) throws Exception {
        verifyInstalled(c);
        if(gameLoader==null) {
            File dex=file(c,"game.dex");
            // Migration guard for any payload imported by an older RC. Android 14+ refuses
            // writable dynamically loaded code when the app targets modern API levels.
            if(!dex.setReadOnly()) throw new IOException("Could not mark game.dex read-only before loading.");
            gameLoader=new DexClassLoader(dex.getAbsolutePath(),c.getCodeCacheDir().getAbsolutePath(),null,c.getClassLoader());
        }
        return gameLoader;
    }

    public static Class<?> gameClass(Context c,String name) throws Exception { return gameClassLoader(c).loadClass(name); }
    public static File midi(Context c,int index) throws IOException {
        if(index<0||index>3) throw new IOException("Invalid MIDI index: "+index);
        File f=file(c,"deadshot_"+index+".mid"); if(!f.isFile()) throw new IOException("DeadShot MIDI payload missing."); return f;
    }
    public static InputStream openSp(Context c) throws IOException {
        File f=file(c,"deadshot.sp"); if(!f.isFile()) throw new IOException("DeadShot scratchpad payload missing."); return new FileInputStream(f);
    }

    private static void verifyInstalled(Context c) throws Exception { verifyDir(dir(c)); }
    private static void verifyDir(File d) throws Exception {
        if(!d.isDirectory()) throw new IOException("DeadShot payload not imported.");
        verify(new File(d,"game.dex"),81552,GAME_DEX_SHA256,"game.dex");
        verify(new File(d,"deadshot.sp"),84064,SP_SHA256,"deadshot.sp");
        int[] sizes={4462,5448,1446,1122};
        for(int i=0;i<4;i++) verify(new File(d,"deadshot_"+i+".mid"),sizes[i],MIDI_SHA256[i],"deadshot_"+i+".mid");
    }
    private static void verify(File f,long size,String expected,String label) throws Exception {
        if(!f.isFile()) throw new IOException("Missing "+label+".");
        if(f.length()!=size) throw new IOException(label+" has the wrong size.");
        String actual=sha256(f); if(!expected.equals(actual)) throw new IOException(label+" failed integrity verification.");
    }
    private static String sha256(File f) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256"); byte[] b=new byte[16384];
        try(InputStream in=new BufferedInputStream(new FileInputStream(f))) { int n; while((n=in.read(b))>=0) if(n>0) md.update(b,0,n); }
        StringBuilder s=new StringBuilder(); for(byte x:md.digest()) s.append(String.format(Locale.US,"%02x",x&255)); return s.toString();
    }
    private static boolean isRequired(String n){ for(String r:REQUIRED) if(r.equals(n)) return true; return false; }
    private static void deleteTree(File f){ if(f==null||!f.exists()) return; if(f.isDirectory()){File[] xs=f.listFiles();if(xs!=null)for(File x:xs)deleteTree(x);} f.delete(); }
    private static void copyTree(File src,File dst) throws IOException {
        if(src.isDirectory()) { if(!dst.mkdirs()&&!dst.isDirectory()) throw new IOException("Could not finalize payload folder."); File[] xs=src.listFiles(); if(xs!=null) for(File x:xs) copyTree(x,new File(dst,x.getName())); }
        else {
            try(InputStream in=new FileInputStream(src);FileOutputStream rawOut=new FileOutputStream(dst)) {
                if("game.dex".equals(dst.getName()) && !dst.setReadOnly())
                    throw new IOException("Could not preserve game.dex as read-only.");
                try(OutputStream out=new BufferedOutputStream(rawOut)) { byte[] b=new byte[16384]; int n; while((n=in.read(b))>=0) if(n>0) out.write(b,0,n); }
            }
        }
    }
}
