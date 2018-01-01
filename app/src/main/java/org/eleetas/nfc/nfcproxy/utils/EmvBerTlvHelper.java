package org.eleetas.nfc.nfcproxy.utils;

import io.github.binaryfoo.DecodedData;
import io.github.binaryfoo.RootDecoder;
import io.github.binaryfoo.TagInfo;
import io.github.binaryfoo.decoders.DecodeSession;

import java.util.List;

/**
 * Created by wangxin on 2015/1/11.
 */
public class EmvBerTlvHelper {
    public static String UnpackAPDU(String s){
        if (s == null) return null;
        StringBuilder UnpackStr=new StringBuilder();

            RootDecoder rootDecoder = new RootDecoder();
            DecodeSession decodeSession =new DecodeSession();
            decodeSession.setTagMetaData( rootDecoder.getTagMetaData("EMV"));
            TagInfo tagInfo = RootDecoder.getTagInfo("apdu-sequence");

            List<DecodedData> decoded = tagInfo.getDecoder().decode(s, 0, decodeSession);
            for (DecodedData d:decoded){
                    UnpackStr.append(""+d.toString()+"\n");
            }

        return UnpackStr.toString();
    }
   public static String getChild(DecodedData decoded){
       StringBuilder retstr=new StringBuilder();
       retstr.append(decoded.toString());
       retstr.append(decoded.getRawData()+" : "+decoded.getDecodedData()+"\n");
       List<DecodedData> child=decoded.getChildren();
       for(DecodedData d:child){


       }
       return retstr.toString();
   }
}
