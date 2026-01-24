package com.davik.adbtools.adb;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.interfaces.RSAPublicKey;
import android.util.Base64;
import android.util.Log;

public class AdbKeyEncoder {
    private static final int KEY_LENGTH_WORDS = 64;
    private static String TAG = "AdbKeyEncoder";

    /**
     * 生成 524 字节的原始二进制公钥（用于连接验证）
     */
    public static byte[] getRawBinary(RSAPublicKey pubkey) {
        BigInteger n = pubkey.getModulus();
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        BigInteger n0inv = n.remainder(r32).modInverse(r32).negate();

        BigInteger r = BigInteger.ZERO.setBit(KEY_LENGTH_WORDS * 32);
        BigInteger rr = r.modPow(BigInteger.valueOf(2), n);

        ByteBuffer bbuf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        bbuf.putInt(KEY_LENGTH_WORDS);
        bbuf.putInt(n0inv.intValue());

        // 写入模数
        for (int i = 0; i < KEY_LENGTH_WORDS; i++) {
            BigInteger[] res = n.divideAndRemainder(r32);
            bbuf.putInt(res[1].intValue());
            n = res[0];
        }
        // 写入 RR 参数
        for (int i = 0; i < KEY_LENGTH_WORDS; i++) {
            BigInteger[] res = rr.divideAndRemainder(r32);
            bbuf.putInt(res[1].intValue());
            rr = res[0];
        }
        bbuf.putInt(pubkey.getPublicExponent().intValue());
        return bbuf.array();
    }

    /**
     * 生成 Base64 格式字符串（仅用于配对或写入 adb_keys 文件）
     */
    public static String convertToAdbFormat(RSAPublicKey pubkey, String identity) {
        byte[] binary = getRawBinary(pubkey);
        String base64 = Base64.encodeToString(binary, Base64.NO_WRAP);
        return base64 + " " + identity; // 配对字符串必须以 \0 结尾
    }

}