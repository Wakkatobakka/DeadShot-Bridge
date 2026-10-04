package com.nttdocomo.io;
import java.io.*;
import javax.microedition.io.Connection;
public interface HttpConnection extends Connection {
    void setRequestMethod(String method) throws IOException;
    void connect() throws IOException;
    InputStream openInputStream() throws IOException;
}
