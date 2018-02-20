package org.eleetas.nfc.nfcproxy.utils;

/**
 * Created by wangxin on 2018/2/19.
 */

import android.nfc.Tag;
import android.nfc.TagLostException;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.eleetas.nfc.nfcproxy.NFCVars;
import org.eleetas.nfc.nfcproxy.R;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

public class WebSocketHelper extends WebSocketClient {
    private final int CONNECT_TIMEOUT = 5000;
    private InetSocketAddress mSockAddr;
    private int mServerPort;
    private String mServerIP;

    public WebSocketHelper(URI serverUri, Draft draft) {
        super(serverUri, draft);
    }

    public WebSocketHelper(URI serverURI) {
        super(serverURI);
    }

    public static void main(String[] args) throws URISyntaxException {
        WebSocketHelper c = new WebSocketHelper(new URI("ws://localhost:8887")); // more about drafts here: http://github.com/TooTallNate/Java-WebSocket/wiki/Drafts
        c.connect();
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        send("Hello, it is me. Mario :)");
        System.out.println("opened connection");
        // if you plan to refuse connection based on ip or httpfields overload: onWebsocketHandshakeReceivedAsClient
    }

    @Override
    public void onMessage(String message) {
        System.out.println("received: " + message);
        try {
          //  log("doInBackground start");
            Socket clientSocket = null;
            BufferedOutputStream clientOS = null;
            BufferedInputStream clientIS = null;
            if (message.indexOf("init") > 0) {
              //  log("command init");
                if(clientSocket!=null){
                //    log("client not null");
                    if(clientSocket.isConnected()){
                 //       log("client to close");
                        clientSocket.close();
                    }
                }
                //log("connecting "+mServerIP+":"+mServerPort);
                mSockAddr = new InetSocketAddress(mServerIP, mServerPort);
                clientSocket = new Socket();
                clientSocket.connect(mSockAddr, CONNECT_TIMEOUT);
                clientOS = new BufferedOutputStream(clientSocket.getOutputStream());
                clientIS = new BufferedInputStream(clientSocket.getInputStream());
                //log(getString(R.string.connected_to_relay));
                //updateStatusUI(getString(R.string.connected_to_relay));
            }

        //socket connect
        } catch (Exception e) {

        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // The codecodes are documented in class org.java_websocket.framing.CloseFrame
        System.out.println("Connection closed by " + (remote ? "remote peer" : "us") + " Code: " + code + " Reason: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
        // if the error is fatal then onClose will be called additionally
    }
}
