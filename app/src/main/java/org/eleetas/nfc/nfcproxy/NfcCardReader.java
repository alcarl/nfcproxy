package org.eleetas.nfc.nfcproxy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.Ndef;
import android.os.Parcelable;

import org.eleetas.nfc.nfcproxy.utils.BasicTagTechnologyWrapper;
import org.eleetas.nfc.nfcproxy.utils.Log;
import org.eleetas.nfc.nfcproxy.utils.TextHelper;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * Created by wangxin on 2018/2/26.
 */

public class NfcCardReader implements NfcAdapter.ReaderCallback {
    private static final String TAG = "NfcCardReader";
    // AID for our loyalty card service.
    private static final String SAMPLE_LOYALTY_CARD_AID = "F222222222";
    // ISO-DEP command HEADER for selecting an AID.
    // Format: [Class | Instruction | Parameter 1 | Parameter 2]
    private static final String SELECT_APDU_HEADER = "00A40400";
    // "OK" status word sent in response to SELECT AID command (0x9000)
    private static final byte[] SELECT_OK_SW = {(byte) 0x90, (byte) 0x00};

    // Weak reference to prevent retain loop. mAccountCallback is responsible for exiting
    // foreground mode before it becomes invalid (e.g. during onPause() or onStop()).
    private WeakReference<AccountCallback> mAccountCallback;
    private boolean mDebugLogging = false;

    public NfcCardReader(AccountCallback accountCallback) {
        mAccountCallback = new WeakReference<AccountCallback>(accountCallback);
    }

    /**
     * Build APDU for SELECT AID command. This command indicates which service a reader is
     * interested in communicating with. See ISO 7816-4.
     *
     * @param aid Application ID (AID) to select
     * @return APDU for SELECT AID command
     */
    public static byte[] BuildSelectApdu(String aid) {
        // Format: [CLASS | INSTRUCTION | PARAMETER 1 | PARAMETER 2 | LENGTH | DATA]
        return HexStringToByteArray(SELECT_APDU_HEADER + String.format("%02X", aid.length() / 2) + aid);
    }

    /**
     * Utility class to convert a byte array to a hexadecimal string.
     *
     * @param bytes Bytes to convert
     * @return String, containing hexadecimal representation.
     */
    public static String ByteArrayToHexString(byte[] bytes) {
        final char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Utility class to convert a hexadecimal string to a byte string.
     * <p>
     * <p>Behavior with input strings containing non-hexadecimal characters is undefined.
     *
     * @param s String containing hexadecimal characters to convert
     * @return Byte array generated from input
     */
    public static byte[] HexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private String getTagInfo(Tag extraTag) {

        String text = "";    //TODO: make this StringBuilder
        //text = extraTag.toString();

        String[] techList = extraTag.getTechList();
        if (techList.length > 0) {
            text += "TechList: ";
            for (String s : techList) {
                text += s + ", ";
            }
            //for now, just choose the first tech in the list
            String tech = techList[0];

        }

        text += "\nNDEF Messages: ";
        Ndef ndef = Ndef.get(extraTag);
        if (ndef != null) {
            try {
                NdefRecord[] ndefRecord = ndef.getNdefMessage().getRecords();
                for (NdefRecord record : ndefRecord) {
                    text += record.toString() + ", ";
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (FormatException e) {
                e.printStackTrace();
            }
        } else
            text += "null";

        text += "\nUID: " + TextHelper.byteArrayToHexString(extraTag.getId()) + "\n";
        return text;

    }

    /**
     * Callback when a new tag is discovered by the system.
     * <p>
     * <p>Communication with the card should take place here.
     *
     * @param tag Discovered tag
     */
    @Override
    public void onTagDiscovered(Tag tag) {
        SharedPreferences prefs = NFCRelayActivity.me.prefs;
        NFCRelayActivity.tag=tag;
        Log.i(TAG, "New tag discovered");

        String TagInfo = getTagInfo(tag);
        NFCRelayActivity.me.updateUI(TagInfo);

        // Android's Host-based Card Emulation (HCE) feature implements the ISO-DEP (ISO 14443-4)
        // protocol.
        //
        // In order to communicate with a device using HCE, the discovered tag should be processed
        // using the IsoDep class.
//        IsoDep isoDep = IsoDep.get(tag);
//        if (isoDep != null) {
//            try {
//                // Connect to the remote NFC device
//                isoDep.connect();
//
//                // Build SELECT AID command for our loyalty card service.
//                // This command tells the remote device which service we wish to communicate with.
//                Log.i(TAG, "Requesting remote AID: " + SAMPLE_LOYALTY_CARD_AID);
//                byte[] command = BuildSelectApdu(SAMPLE_LOYALTY_CARD_AID);
//                // Send command to remote device
//                Log.i(TAG, "Sending: " + ByteArrayToHexString(command));
//                byte[] result = isoDep.transceive(command);
//                // If AID is successfully selected, 0x9000 is returned as the status word (last 2
//                // bytes of the result) by convention. Everything before the status word is
//                // optional payload, which is used here to hold the account number.
//                int resultLength = result.length;
//                byte[] statusWord = {result[resultLength - 2], result[resultLength - 1]};
//                byte[] payload = Arrays.copyOf(result, resultLength - 2);
//                if (Arrays.equals(SELECT_OK_SW, statusWord)) {
//                    // The remote NFC device will immediately respond with its stored account number
//                    String accountNumber = new String(payload, "UTF-8");
//                    Log.i(TAG, "Received: " + accountNumber);
//                    // Inform CardReaderFragment of received account number
//                    mAccountCallback.get().onAccountReceived(accountNumber);
//                }
//            } catch (IOException e) {
//                Log.e(TAG, "Error communicating with card: " + e.toString());
//            }
//        }
    }

    public interface AccountCallback {
        public void onAccountReceived(String account);
    }

}
