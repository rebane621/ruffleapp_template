package com.github.rebane621.ruffleapk;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;

public class AssetHTTPServer implements Runnable {

    ServerSocket listen;
    int localPort;
    boolean running;
    Context context;

    Logger logger;

    public interface ReadyCallback {
        void ready(int port);
    }
    ReadyCallback onReadyCb;

    public AssetHTTPServer(Context context) {
        this.context = context;
        logger = Logger.getLogger("AssetHTTPServer");
    }

    public void onReady(ReadyCallback ready) {
        onReadyCb = ready;
    }

    public void run() {
        //create a dummy client socket to find a free port
        localPort = 50000;
        while (true) {
            try {
                listen = new ServerSocket(localPort, 1, Inet4Address.getLocalHost());
                listen.setSoTimeout(500);
                listen.setReuseAddress(true);
                break;
            } catch (IOException e) {
                localPort ++;
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Could not find free port for socket");
            }
        }
        running = true;
        logger.info("Bound to port "+localPort);
        onReadyCb.ready(localPort);

        while (running) {
            try {
                Socket connection = listen.accept();
                connection.setSoTimeout(5000);
                while (reply(connection)) {/*multiple requests if keep alive?*/}
                connection.close();
            } catch (SocketTimeoutException ignore) {
                continue;
            } catch (IOException e) {
                logger.info("Socket error: "+e.getMessage());
                running = false;
            }
        }
        logger.info("Closed at "+localPort);
        try {
            listen.close();
        } catch (Throwable ignore) {}
    }

    private String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b; int p=0;
        while ((b=input.read())!=-1) {
            if (b == 0 || (b == '\n' && p == '\r') ) break;
            baos.write(b);
            p=b;
        }
        if (baos.size()==0) return null; //not even a \r for \n == immediate -1 or eot
        return baos.toString().trim();
    }

    private String getServerTime(Date when) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(when);
    }

    /** @return keep_alive */
    private boolean reply(Socket connection) throws IOException {
        InputStream inraw = connection.getInputStream();
        String line;
        boolean first=true;
        String method="";
        String path="";
        String httpV="";
        String user_agent=""; //simple filtering
        boolean keep_alive = false;
        while ((line=readLine(inraw))!=null && !line.isEmpty()) { //read head
            if (first) {
                int a = line.indexOf(' '), b = line.lastIndexOf(' ');
                method = line.substring(0, a);
                path = line.substring(a+1, b);
                httpV = line.substring(b+1);
                first = false;
            } else {
                if (line.startsWith("User-Agent: ")) {
                    user_agent = line.substring(12);
                } else if (line.startsWith("Connection: ")) {
                    keep_alive = line.substring(12).equalsIgnoreCase("keep-alive");
                }
            }
        }
        //while (br.readLine() != null) {/*seek*/}
        logger.info(connection.getRemoteSocketAddress() + " requested " + path + " with " + method);


        Map<String,String> fields = new HashMap<>();
        fields.put("Server", "AssetsProxy");
        fields.put("Content-Type", "text/plain");
//        fields.put("Content-Encoding", "deflate");
//        fields.put("Transfer-Encoding", "deflate");
        fields.put("Last-Modified", getServerTime(new Date(0L)));
        fields.put("Date", getServerTime(Calendar.getInstance().getTime()));
        int recode = 200;
        String rename = "OK";
        InputStream in = null;

        if (!user_agent.equals("RuffleAPK/1.0 (Internal)")) {
            recode = 404;
            rename = "Not Found";
        } else if (!method.equalsIgnoreCase("GET")) {
            recode = 405;
            rename = "Method Not Allowed";
        } else if (path.contains("..")) { //most stupid traversal protection
            recode = 400;
            rename = "Bad Request";
        } else {
            try {
                if (path.equals("/")) {
                    String[] files = context.getAssets().list("www");
                    if (files != null) for (String file : files) {
                        if (file.startsWith("index.")) {
                            path = "/" + file;
                            break;
                        }
                    }
                }

                logger.info("Serving: assets/www"+path);
                in = context.getAssets().open("www"+path, AssetManager.ACCESS_STREAMING);
                //lul, can't rely on FDs
                long size = 0; byte[] buffer = new byte[4096]; int read;
                while ((read = in.read(buffer, 0, buffer.length))>=0) size += read;
                in.close();
                in = context.getAssets().open("www"+path, AssetManager.ACCESS_STREAMING);
                //add headers
                logger.info("> Content: "+guessMimeType(path)+", "+size+" B");
                fields.put("Content-Length", String.valueOf(size));
                fields.put("Content-Type", guessMimeType(path)+"; charset=utf-8");
            } catch (IOException ignore) {
                logger.warning(ignore.getMessage());
                recode = 404;
                rename = "Not Found";
            }
        }
        logger.info("> Replying "+recode+" "+rename);

        //raw dogging this stream because we might write octet stream files
        OutputStream out = connection.getOutputStream();
        out.write((httpV + " " + recode + " " + rename + "\r\n").getBytes(StandardCharsets.US_ASCII));
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            out.write((entry.getKey() + ": " + entry.getValue() + "\r\n").getBytes(StandardCharsets.US_ASCII));
        }
        out.write(("\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        if (in != null) {
            byte[] buffer = new byte[4096];
            int read; int total=0;
            while ((read=in.read(buffer,0, buffer.length))>=0) {
                total += read;
                out.write(buffer, 0, read);
            }
            logger.info("Wrote body with "+total+" bytes");
            in.close();
        } else {
            out.write(("<!DOCTYPE html><html><body><h1>"+recode+" "+rename+"</h1><p>Something went wrong</p></body></html>").getBytes(StandardCharsets.UTF_8));
        }
        out.flush();
        return keep_alive;
    }

    private static String guessMimeType(String filename) {
        if (filename.endsWith(".swf")) {
            return "application/x-shockwave-flash";
        }
        return URLConnection.guessContentTypeFromName(filename);
    }

}
