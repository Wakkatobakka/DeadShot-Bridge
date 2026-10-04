package com.wakka.deadshot;

import android.content.Context;
import java.io.*;
import java.util.Calendar;
import java.util.TimeZone;

public final class ScratchpadStore {
    private static final Object LOCK = new Object();
    private static byte[] data;
    private static File backing;

    private ScratchpadStore() {}

    public static void init(Context context) throws IOException {
        synchronized (LOCK) {
            backing = new File(context.getFilesDir(), "deadshot-scratchpad.bin");
            DeadshotDiagnostics.event("SCRATCHPAD", "Initialize app-private backing: " + backing.getName());
            if (backing.isFile() && backing.length() == 84000) {
                DeadshotDiagnostics.detail("Scratchpad source", "Existing private 84000-byte save");
                data = readAll(new FileInputStream(backing));
            } else {
                DeadshotDiagnostics.detail("Scratchpad source", "Imported verified deadshot.sp seed");
                byte[] wrapped = readAll(PayloadStore.openSp(context));
                if (wrapped.length == 84064) {
                    data = new byte[84000];
                    System.arraycopy(wrapped, 64, data, 0, 84000);
                } else if (wrapped.length == 84000) {
                    data = wrapped;
                } else {
                    throw new IOException("Unexpected DeadShot scratchpad size: " + wrapped.length);
                }
            }

            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("JST"));
            writeIntBE(data, 28, cal.get(Calendar.MONTH) + 1);
            persistLocked();
            DeadshotDiagnostics.detail("Scratchpad bytes", Integer.toString(data.length));
        }
    }

    public static InputStream input(int pos) throws IOException {
        synchronized (LOCK) {
            ensure();
            DeadshotDiagnostics.count("Scratchpad input stream requests");
            DeadshotDiagnostics.event("SCRATCHPAD READ", "position=" + pos);
            if (pos < 0 || pos > data.length) throw new IOException("scratchpad pos " + pos);
            return new ByteArrayInputStream(data, pos, data.length - pos);
        }
    }

    public static OutputStream output(final int pos) throws IOException {
        synchronized (LOCK) {
            ensure();
            if (pos < 0 || pos > data.length) throw new IOException("scratchpad pos " + pos);
        }
        return new ByteArrayOutputStream() {
            @Override public void close() throws IOException {
                super.close();
                byte[] out = toByteArray();
                synchronized (LOCK) {
                    if (pos + out.length > data.length) throw new IOException("scratchpad overflow");
                    System.arraycopy(out, 0, data, pos, out.length);
                    persistLocked();
                }
            }
        };
    }

    public static byte[] snapshot() {
        synchronized (LOCK) { return data == null ? null : data.clone(); }
    }

    private static void ensure() throws IOException {
        if (data == null) throw new IOException("Scratchpad not initialized");
    }

    private static void persistLocked() throws IOException {
        DeadshotDiagnostics.count("Scratchpad persist attempts");
        if (backing == null) return;
        FileOutputStream fos = new FileOutputStream(backing);
        try { fos.write(data); fos.getFD().sync(); }
        finally { fos.close(); }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally { in.close(); }
    }

    private static void writeIntBE(byte[] b, int p, int v) {
        b[p] = (byte)(v >>> 24); b[p+1] = (byte)(v >>> 16); b[p+2] = (byte)(v >>> 8); b[p+3] = (byte)v;
    }
}
