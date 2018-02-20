package org.eleetas.nfc.nfcproxy.utils;

public class TextHelper {
    public static String byteArrayToHexString(byte[] b) {
        //wx 20150110 统一转换hex格式
    	//return byteArrayToHexString(b, "0x", " ", false);
        StringBuilder RetString=new StringBuilder();
        String HexString=byteArrayToHexString(b, "", "", false);
        if (HexString == null) return null;
        String HexStringUnpack=EmvBerTlvHelper.UnpackAPDU(HexString);
        RetString.append(HexString).append("\n").append(HexStringUnpack); //

        return RetString.toString();
    }
    
	public static String byteArrayToHexString(byte[] b, String hexPrefix, String hexSuffix, boolean cast) {
		if (b == null) return null;
		StringBuffer sb = new StringBuffer(b.length * 2);
		for (int i = 0; i < b.length; i++) {			
			int v = b[i] & 0xff;
			if (cast && v > 0x7f) {
				sb.append("(byte)");
			}
			sb.append(hexPrefix);
			if (v < 16) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(v));
			if (i + 1 != b.length) {
				sb.append(hexSuffix);
			}
		}
		return sb.toString();
	}
}
