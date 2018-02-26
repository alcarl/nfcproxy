package org.eleetas.nfc.nfcproxy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.cardemulation.HostApduService;
import android.os.AsyncTask;
import android.os.Bundle;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.NfcAdapter;
import android.os.PowerManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.Base64;
import android.view.ActionMode;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.eleetas.nfc.nfcproxy.utils.IOUtils;
import org.eleetas.nfc.nfcproxy.utils.LogHelper;
import org.eleetas.nfc.nfcproxy.utils.TagHelper;
import org.eleetas.nfc.nfcproxy.utils.TextHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by wangxin on 2018/2/20.
 */

public class MyHostApduService extends HostApduService {
    private static final int PROXY_MODE = 0;
    private static final int REPLAY_PCD_MODE = 1;
    private static final int REPLAY_TAG_MODE = 2;
    public static NFCProxyActivity nfcProxyActivity = null;
    private final int CONNECT_TIMEOUT = 5000;
    private final String DEFAULT_SALT = "kAD/gd6tvu8=";
    Bundle requests = new Bundle();
    Bundle responses = new Bundle();
    ProxyTask proxyTask;
    private ScrollView mStatusTab;
    private TextView mStatusView;
    private TextView mDataView;
    private ScrollView mDataTab;
    private TableLayout mDataTable;
    private TabHost mTabHost;
    private ListView mSavedList;
    private Menu mOptionsMenu;
    private ActionMode mActionMode;
    private InetSocketAddress mSockAddr;
    private DBHelper mDBHelper;
    private SecretKey mSecret = null;
    private String mSalt = null;
    private View mSelectedSaveView;
    private int mSelectedId = 0;
    private Bundle mSessions = new Bundle();
    private Bundle mReplaySession;
    private PowerManager.WakeLock mWakeLock;
    private int mMode = PROXY_MODE;
    private boolean mDebugLogging = false;
    private int mServerPort;
    private String mServerIP;
    private boolean mEncrypt = true;
    private boolean mMask = false;
    private Socket clientSocket = null;
    private BufferedOutputStream clientOS = null;
    private BufferedInputStream clientIS = null;
    private long startTime = System.currentTimeMillis();

    public static String bytesToHexString(byte[] bytes) {

        if (bytes == null) {
            return "";
        }
        StringBuffer buff = new StringBuffer();
        int len = bytes.length;
        for (int j = 0; j < len; j++) {
            if ((bytes[j] & 0xff) < 16) {
                buff.append('0');
            }
            buff.append(Integer.toHexString(bytes[j] & 0xff));
        }
        return buff.toString().toUpperCase();
    }

    private void log(Object msg) {
        if (mDebugLogging) {
            LogHelper.log(this, msg);

        }
    }

    private void addLineBreak(int id) {
        TableRow line = new TableRow(this);
        line.setLayoutParams(new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        line.setBackgroundColor(Color.GREEN);
        TextView ltv = new TextView(this);
        ltv.setHeight(1);
        line.addView(ltv);
        line.setTag(id);
        mDataTable.addView(line);
    }


    private View.OnLongClickListener getTransactionsTextViewLongClickListener() {
        return new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View view) {
                if (mActionMode != null) {
                    return false;
                }
                view.setSelected(true);
                mSelectedId = view.getId();
                log("selectedID: " + mSelectedId);
                mActionMode = nfcProxyActivity.startActionMode(nfcProxyActivity.mTransactionsActionModeCallback);
                return true;
            }
        };
    }

    private View.OnLongClickListener getSavedTextViewLongClickListener() {
        return new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View view) {
                if (mActionMode != null) {
                    return false;
                }

                view.setSelected(true);
                mSelectedSaveView = view;
                mActionMode = nfcProxyActivity.startActionMode(nfcProxyActivity.mSavedActionModeCallback);
                return true;
            }
        };
    }
    private void storeTransactionsAndBreak(Bundle requests, Bundle responses) {
        //trans finish
        //save data
        final Bundle session = new Bundle();
        session.putBundle("requests", requests);
        session.putBundle("responses", responses);
        //set longclick pop menu event listener for delete export
        mDataView.setOnLongClickListener(getTransactionsTextViewLongClickListener());
        mDataTable.post(new Runnable() {
            @Override
            public void run() {
                if (mDataView != null && mDataView.getText().length() > 0) {
                    //TODO: ...might be race condition here XXX
                    mSessions.putBundle(String.valueOf(mSessions.size()), session);
                    addLineBreak(mSessions.size() - 1);
                    mDataTab.fullScroll(ScrollView.FOCUS_DOWN);
                    mDataView = null;
                }
            }
        });
    }

    private SecretKey generateSecretKey() throws IOException {
        try {
            SharedPreferences prefs = getSharedPreferences(NFCVars.PREFERENCES, MODE_PRIVATE);
            SecretKeyFactory f;
            f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] salt = Base64.decode(mSalt, Base64.DEFAULT);
            KeySpec ks = new PBEKeySpec(prefs.getString("passwordPref", getString(R.string.default_password)).toCharArray(), salt, 2000, 256);
            SecretKey tmp = f.generateSecret(ks);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            log(e);
            throw new IOException(e);
        }
    }

    private void updateStatusUI(final CharSequence msg) {
        mStatusView.post(new Runnable() {
            @Override
            public void run() {
                mStatusView.append(TextUtils.concat(msg, "\n"));
                mStatusTab.post(new Runnable() {
                    @Override
                    public void run() {
                        mStatusTab.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void updateDataUI(final CharSequence msg) {
        try {
            mDataView.post(new Runnable() {
                @Override
                public void run() {
                    try {
                    mDataView.append(TextUtils.concat(msg, "\n"));
                    mDataTab.fullScroll(ScrollView.FOCUS_DOWN);
                    } catch (NullPointerException e) {
                        System.out.print("");
                    }
                }
            });
        } catch (NullPointerException e) {
            System.out.print("");
        }
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        System.out.println("processCommandApdu()--收到的指令：" + bytesToHexString(commandApdu));
//        return "9000".getBytes();

        if (mDataView == null) {
            //TODO: maybe convert table to ListView

            mDataView = nfcProxyActivity.mDataView;
            mDataTable = nfcProxyActivity.mDataTable;
            mStatusView = nfcProxyActivity.mStatusView;
            mSessions = nfcProxyActivity.mSessions;
            mStatusTab = nfcProxyActivity.mStatusTab;
            mDataTab = nfcProxyActivity.mDataTab;
            if (mDataView == null) {
                TableRow row = new TableRow(this);
                row.setLayoutParams(new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                mDataView = new TextView(this);
                mDataView.setFreezesText(true);
                mDataView.setId(mSessions.size());
                row.addView(mDataView);
                mDataTable.addView(row);
            }
            SharedPreferences prefs = nfcProxyActivity.prefs;

            if (prefs.getBoolean("screenPref", true)) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, getString(R.string.app_name));
                mWakeLock.acquire();
            }

            mDebugLogging = prefs.getBoolean("debugLogPref", false);
            mSalt = prefs.getString("saltPref", DEFAULT_SALT);
            mServerPort = prefs.getInt("portPref", Integer.parseInt(getString(R.string.default_port)));
            mServerIP = prefs.getString("ipPref", getString(R.string.default_ip));
            mEncrypt = prefs.getBoolean("encryptPref", true);

            requests = new Bundle();
            responses = new Bundle();

        }
        proxyTask = new ProxyTask();
        proxyTask.myHostApduService=this;
        proxyTask.execute(commandApdu);

        return null;
    }

    @Override
    public void onDeactivated(int reason) {
        System.out.println("onDeactivated():" + reason);
        //disconnect PCD
        try {
             clientSocket.close();
        } catch (Exception e) {

        }
        clientSocket=null;
        //save process time
        updateDataUI(getString(R.string.time) + ": " + (System.currentTimeMillis() - startTime));
        log(getString(R.string.transaction_complete));
        updateStatusUI(getString(R.string.transaction_complete));

        if (mDataView == null) {
            log("mDataView null"); //??? happens on quick reads? activity is recreated with
        } else {
            //Finish
            storeTransactionsAndBreak(requests, responses);
        }



    }


    public class ProxyTask extends AsyncTask<byte[], Void, Void> {
        public  MyHostApduService myHostApduService;
        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onCancelled(java.lang.Object)
         */
        @Override
        protected void onCancelled(Void result) {
            // TODO Auto-generated method stub
            super.onCancelled(result);
        }

        @Override
        protected Void doInBackground(byte[]... params) {
            byte[] commandApdu = params[0];
            log("doInBackground start");

            try {
                byte[] pcdRequest = null;
                byte[] cardResponse = null;
                String tagStr = getString(R.string.tag) + ": ";
                String pcdStr = getString(R.string.pcd) + ": ";
                SpannableString msg = new SpannableString("");

                if (clientSocket == null || clientSocket.isConnected() == false) {
                    //socket no connect
                    //first touch PCD
                    //save start time
                    startTime = System.currentTimeMillis();

                    log(getString(R.string.connecting_to_relay));
                    updateStatusUI(getString(R.string.connecting_to_relay));

                    mSockAddr = new InetSocketAddress(mServerIP, mServerPort);
                    clientSocket = new Socket();
                    clientSocket.connect(mSockAddr, CONNECT_TIMEOUT);
                    clientOS = new BufferedOutputStream(clientSocket.getOutputStream());
                    clientIS = new BufferedInputStream(clientSocket.getInputStream());
                    log(getString(R.string.connected_to_relay));
                    updateStatusUI(getString(R.string.connected_to_relay));


                    try {
                        log("sending ready");
                        IOUtils.sendSocket((NFCVars.READY + "\n").getBytes("UTF-8"), clientOS, null, false);
                        String line = IOUtils.readLine(clientIS);
                        log("command: " + line);
                        if (line.equals(NFCVars.NOT_READY)) {
                            updateStatusUI(getString(R.string.nfcrelay_not_ready));
                            log(getString(R.string.nfcrelay_not_ready));
                            return null;
                        } else if (!line.equals(NFCVars.OPTIONS)) {
                            updateStatusUI(getString(R.string.unknown_command));
                            log(getString(R.string.unknown_command));
                            return null;
                        }
                        if (mEncrypt) {
                            IOUtils.sendSocket((NFCVars.ENCRYPT + "\n").getBytes("UTF-8"), clientOS, null, false);
                            IOUtils.sendSocket(Base64.decode(mSalt, Base64.DEFAULT), clientOS, null, false);

                            if (mSecret == null) {
                                mSecret = generateSecretKey();
                            }
                            byte[] verify = IOUtils.readSocket(clientIS, mSecret, mEncrypt);
                            if (verify == null) {
                                updateStatusUI(getString(R.string.unexpected_response_encrypting));
                                log(getString(R.string.unexpected_response_encrypting));
                                log(TextHelper.byteArrayToHexString(verify));
                                return null;
                            } else if (!new String(verify, "UTF-8").equals(NFCVars.VERIFY)) {
                                updateStatusUI(getString(R.string.bad_password));
                                log(getString(R.string.bad_password));
                                log(TextHelper.byteArrayToHexString(verify));
                                IOUtils.sendSocket(NFCVars.BAD_PASSWORD.getBytes("UTF-8"), clientOS, mSecret, mEncrypt);
                                return null;
                            }
                            IOUtils.sendSocket(NFCVars.OK.getBytes("UTF-8"), clientOS, mSecret, mEncrypt);

                        } else {
                            IOUtils.sendSocket((NFCVars.CLEAR + "\n").getBytes("UTF-8"), clientOS, null, false);
                        }

                        log("getting id");
                        byte[] id = IOUtils.readSocket(clientIS, mSecret, mEncrypt);
                        if (id == null) {
                            updateStatusUI(getString(R.string.error_getting_id));
                            log(getString(R.string.error_getting_id));
                            return null;
                        }
                        log("response: " + TextHelper.byteArrayToHexString(id));
                        log(new String(id));


                        try {

                            //send id to pcd not need
//                        meth = cls.getMethod("transceive", new Class[]{byte[].class});    //TODO: check against getMaxTransceiveLength()
//                        pcdRequest = (byte[]) meth.invoke(ipcd, id);

                            //save card id
                            msg = new SpannableString(tagStr + TextHelper.byteArrayToHexString(id));
                            msg.setSpan(new UnderlineSpan(), 0, 4, 0);
                            responses.putByteArray(String.valueOf(responses.size()), id);
                            updateDataUI(msg);
                            log("sent id to pcd: " + TextHelper.byteArrayToHexString(id));

                            //pcd send select PSE
                            pcdRequest = commandApdu;
                            // save select PSE
                            msg = new SpannableString(pcdStr + TextHelper.byteArrayToHexString(pcdRequest));
                            msg.setSpan(new UnderlineSpan(), 0, 4, 0);
                            requests.putByteArray(String.valueOf(requests.size()), pcdRequest);
                            updateDataUI(msg);
                            log("response from PCD: " + TextHelper.byteArrayToHexString(pcdRequest));
                            log(new String(pcdRequest));

                            //send select PSE to card
                            IOUtils.sendSocket(pcdRequest, clientOS, mSecret, mEncrypt);
                            log("sent response to relay/card");
                            cardResponse = IOUtils.readSocket(clientIS, mSecret, mEncrypt);

                            if (cardResponse != null) {
                                if (new String(cardResponse, "UTF-8").equals("Relay lost tag")) {
                                    //receive Relay lost tag  ,send null to PCD
                                    updateStatusUI(getString(R.string.relay_lost_tag));
                                    log(getString(R.string.relay_lost_tag));
                                    return null;
                                }
                            } else {
                                updateStatusUI(getString(R.string.bad_crypto));
                                log(getString(R.string.bad_crypto));
                                return null;
                            }

                            log("relay/card response: " + TextHelper.byteArrayToHexString(cardResponse));
                            log("sending card response to PCD");

                            if (mMask && cardResponse[0] == 0x70) {
                                msg = new SpannableString(tagStr + getString(R.string.masked));
                            } else {
                                msg = new SpannableString(tagStr + TextHelper.byteArrayToHexString(cardResponse));
                            }
                            msg.setSpan(new UnderlineSpan(), 0, 4, 0);
                            responses.putByteArray(String.valueOf(responses.size()), cardResponse);
                            updateDataUI(msg);

                            myHostApduService.sendResponseApdu(cardResponse);

                        } catch (IOException e) {
                            log(e);
                            updateStatusUI(getString(R.string.ioexception_error_writing_socket));
                        } finally {


                        }
                    } catch (UnsupportedEncodingException e) {
                        log(e);
                        updateStatusUI("UnsupportedEncodingException");
                    }

//                    if (mDataView == null) {
//                        log("mDataView null"); //??? happens on quick reads? activity is recreated with
//                    } else {
//                        //Finish
//                        storeTransactionsAndBreak(requests, responses);
//                    }
                } else {
                    //socket connected process req resp
                    pcdRequest = commandApdu;

                    //save and show data from PCD
                    log("response from PCD: " + TextHelper.byteArrayToHexString(pcdRequest));
                    requests.putByteArray(String.valueOf(requests.size()), pcdRequest);
                    msg = new SpannableString(pcdStr + TextHelper.byteArrayToHexString(pcdRequest));
                    msg.setSpan(new UnderlineSpan(), 0, 4, 0);
                    updateDataUI(msg);

                    IOUtils.sendSocket(pcdRequest, clientOS, mSecret, mEncrypt);
                    log("sent response to relay/card");

                    cardResponse = IOUtils.readSocket(clientIS, mSecret, mEncrypt);
                    if (cardResponse != null) {
                        if (new String(cardResponse, "UTF-8").equals("Relay lost tag")) {
                            updateStatusUI(getString(R.string.relay_lost_tag));
                            log(getString(R.string.relay_lost_tag));
                            return null;
                        }
                    } else {
                        updateStatusUI(getString(R.string.bad_crypto));
                        log(getString(R.string.bad_crypto));
                        return null;
                    }

                    log("relay/card response: " + TextHelper.byteArrayToHexString(cardResponse));
                    log("sending card response to PCD");

                    if (mMask && cardResponse[0] == 0x70) {
                        msg = new SpannableString(tagStr + getString(R.string.masked));
                    } else {
                        msg = new SpannableString(tagStr + TextHelper.byteArrayToHexString(cardResponse));
                    }

                    msg.setSpan(new UnderlineSpan(), 0, 4, 0);
                    responses.putByteArray(String.valueOf(responses.size()), cardResponse);
                    updateDataUI(msg);

                    myHostApduService.sendResponseApdu(cardResponse);


                }

            } catch (SocketTimeoutException e) {
                log(e);
                updateStatusUI(getString(R.string.connection_to_relay_timed_out));
            } catch (ConnectException e) {
                log(getString(R.string.connection_to_relay_failed));
                updateStatusUI(getString(R.string.connection_to_relay_failed));
            } catch (SocketException e) {
                log(e);
                updateStatusUI(getString(R.string.socket_error) + " " + e.getLocalizedMessage());
            } catch (UnknownHostException e) {
                updateStatusUI(getString(R.string.unknown_host));
            } catch (IOException e) {
                log(e);
                updateStatusUI("IOException: " + e.getLocalizedMessage());
            } catch (final Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                log(getString(R.string.something_happened) + ": " + e.toString() + " " + sw.toString());
                updateStatusUI(getString(R.string.something_happened) + ": " + e.toString() + " " + sw.toString());
            } finally {
//                try {
//                    log("Closing connection to NFCRelay...");
//                    if (clientSocket != null)
//                        clientSocket.close();
//                } catch (IOException e) {
//                    log("error closing socket: " + e);
//                }
//                log("doInBackground end");
            }
            return null;
        }

        private void updateStatusUI(final CharSequence msg) {
            mStatusView.post(new Runnable() {
                @Override
                public void run() {
                    mStatusView.append(TextUtils.concat(msg, "\n"));
                    mStatusTab.post(new Runnable() {
                        @Override
                        public void run() {
                            mStatusTab.fullScroll(ScrollView.FOCUS_DOWN);
                        }
                    });
                }
            });
        }

        private void updateDataUI(final CharSequence msg) {
            try {

            mDataView.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mDataView.append(TextUtils.concat(msg, "\n"));
                        mDataTab.fullScroll(ScrollView.FOCUS_DOWN);
                    } catch (NullPointerException e) {
                        System.out.print("");
                    }
                }
            });
            } catch (NullPointerException e) {
                System.out.print("");
            }

        }
    }

//    private void checkIsDefaultApp() {
//        CardEmulation cardEmulationManager = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(this));
//        ComponentName paymentServiceComponent = new ComponentName(getApplicationContext(), CardMangerService.class.getCanonicalName());
//        if (!cardEmulationManager.isDefaultServiceForCategory(paymentServiceComponent, CardEmulation.CATEGORY_PAYMENT)) {
//            Intent intent = new Intent(CardEmulation.ACTION_CHANGE_DEFAULT);
//            intent.putExtra(CardEmulation.EXTRA_CATEGORY,CardEmulation.CATEGORY_PAYMENT);
//            intent.putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT,paymentServiceComponent);
//            startActivityForResult(intent, 0);
//            L.d("TAG","当前应用不是默认支付，需手动设置");
//        } else {
//            L.d("TAG","当前应用是系统默认支付程序");
//        }
//    }

}
