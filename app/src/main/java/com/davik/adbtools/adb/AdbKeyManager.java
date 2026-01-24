package com.davik.adbtools.adb;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

//import dadb.AdbKeyPair;

public class AdbKeyManager {

   /* private static final String TAG = "AdbKeyManager";

    // =======================================================================
    // 🔴 请把这里替换为你电脑上 C:\Users\用户名\.android\adbkey 文件里的内容
    // 注意：只复制 -----BEGIN... 和 END... 中间的那一大串 Base64 内容
    // 不要带头尾，不要带换行，复制成一行长字符串
    // =======================================================================
    private static final String PRIVATE_KEY_BASE64 =
            "-----BEGIN PRIVATE KEY-----\n" +
                    "MIIEuwIBADANBgkqhkiG9w0BAQEFAASCBKUwggShAgEAAoIBAQDcDgM6YVNsEMVK43RoI0XCzYaf\n" +
                    "0y3fdOr2tPPbR3qVIF69v5BQ9JARX0YeG3eHHip+7lK3R+MJ4Ll+dvpe3kumyC1n9VNdhLDLwUZv\n" +
                    "jjbE3Z+ChcUpHdRnRG6GbC9fdx4cgMs+p89+KJQ4Hhm2TphtpskhLZ/IuV+JuCt0oKcSAiQ3AIWS\n" +
                    "g2ey1nyTxrnueLFIaG1a7YzxPN0mWqo71morD/QTo/7LmpO2wIkjKCdUszQr8ZmesLZgpva6hVOz\n" +
                    "tHviqBI05EIwp/hVT5e1jddC3hRSzqjIOUGtYDWs5te456NOHbhMUr6RM7hVQcDEn+YZ6z2CEkOi\n" +
                    "6l9D48hzw78dAgMBAAECggEAEiyrGhWDOxPog46lJNuy7YulgIpDyeahaFZaJKRy7KGjiYbqj7Ef\n" +
                    "O/wTMbXhiZyVdG3REZejmgOANoBzncW82E3EqEckBz+IDZANNfX4Moq+W5yaQ9LVoeDOQxZo1Djl\n" +
                    "mQOPJcHc2oXV9guQYpT7tGS3zGB1B7I9wIVgO23wVoWE+fM8n/ziEMWEeWmH0XKYPiRF2+XvruRJ\n" +
                    "E2CQdB2d08VIK1dLGvefzbRIWjvxp3zbai+lXLONf9j5oChD3riqRSZEDLhfEPdTvRcNroQ322KI\n" +
                    "VIApB3i/wnrR9rCRy9pUkW972YtJVfbrB2DB/rvSAJpBAHi7AIpQMSWsBl0UuQKBgQD8ZwwAt/pT\n" +
                    "XjGDCYDppItoMXnb8ApjK8TFi60QsQLLkw3z8r1j4daby+E/FAf+8MErxeaxCEHFQfUUf9gqWIc5\n" +
                    "EBCBff/8aRAJRS+ew3vv+OQNPice9w7izETpl6WOWD0UJlI98RBcdJSdSD4sfwyA+cSh9/4sGwhf\n" +
                    "8IKrNtM39QKBgQDfMO/SQRuTT/hVbV/yCQqQe67TPr8YqI+inEqYGmO4cjysd+cv4eRIQeRuFfgv\n" +
                    "s2nffFn8F69QTqpvlwPCPYJGiEPdtmkf5EJ/CP451P/mz6bG6R6CZa9CvUa9suDZ7cptrl9Vp1cC\n" +
                    "hu4pKpuI2ardvsTCc/4BjJLlZc62xSh5iQKBgQDyfj414owJ1JFVB6hm0MNu0zn0aGZSaRafhFY9\n" +
                    "lxLMavgYy7nRYIRDIlnBtIkKElxEpdnBc+FclsXTcBBKfstLs3doMZni8z2I6oOZ0M4d+81+O5xy\n" +
                    "T4Jpuz7p/dv9KiFkXnzdLeE+MnMluOai8V0d/NlBk3ULAt3E7tlbjUzYkQKBgGRN7Om0Av2Qag2S\n" +
                    "axUuRQYr36TmRSGC15J/5PI4oepJxMTJ2idlwJ9MpalnzDM70W/zxckKp+pSgDxIRRMta3fhU4a3\n" +
                    "rMT9Y3kOBJrfA+aqGcy5BdIaespmn/0u/+hQ+rGSNRwcaXkkRLJRiLbXA/hF/M1bLaNqlrXrrBvt\n" +
                    "vUPpAn8rWki+iOLuUUX44V8dMysX6mpjmxGGi7r8WbFEzpWYaaUKjhuKTKc6uBfckdZoq35scMdN\n" +
                    "BmgW0zQIG+MLbRqaaZlIpQbhuhcJBq5MaZt4MN0unG4A5YxOiONZkppQOlXLZ//2QR/YAkT9ej+N\n" +
                    "9ISS62LKZMTZG8MQ6mhWKT6X"+
                    "-----END PRIVATE KEY-----\n";

    // 🔴 请把这里替换为你电脑上 adbkey.pub 的内容
    // 格式通常是：ssh-rsa AAAA... user@host
    private static final String PUBLIC_KEY_STRING =
            "QAAAAMsEB6cdv8NzyONDX+qiQxKCPesZ5p/EwEFVuDORvlJMuB1Oo+e41+asNWCtQTnIqM5SFN5C1421l09V+KcwQuQ0Eqjie7SzU4W69qZgtrCemfErNLNUJygjicC2k5rL/qMT9A8ratY7qlom3TzxjO1abWhIsXjuucaTfNayZ4OShQA3JAISp6B0K7iJX7nIny0hyaZtmE62GR44lCh+z6c+y4AcHndfL2yGbkRn1B0pxYWCn93ENo5vRsHLsIRdU/VnLcimS95e+nZ+ueAJ40e3Uu5+Kh6HdxseRl8RkPRQkL+9XiCVekfb87T26nTfLdOfhs3CRSNodONKxRBsU2E6Aw7cK/Q/wkLDgGAqzfTo12BiEO2WahQDC4MCqen7GWPZ/VwykcvrBxgsJn8xpEWPhR0lme40KNBYQBPi+ar5xO34wTMneLoV/5KUBuzSwi2xkcIWOf+61a6kNiFDByr+jWPvI7KZAFjQIeCW7Lc+GDKfNcbix8lw74f38eGoSSXuVVfYE4H6sfGn7hFfBup436X/1TXkk/jRXI7Gpye2+Q7bTqKrHc9u+gKhBBb3A2BV6boXnbaPcmoVDs5rJZ2ANtlGzHRoP7nwuzWWuslqcgvlG8bw+TLym5p8KJpfFuGZUzR/H7VpINIP7swZGYsuKwiYcxG4P11a6RFmPX3maomCUAEAAQA= adbtools@android";

    *//**
     * 获取静态的 ADB 密钥对
     * 直接使用电脑的密钥，确保 100% 稳定不弹窗
     *//*
    public static AdbKeyPair getOrGenerateAdbKeyPair(Context context) {
        try {
            Log.i(TAG, "正在加载静态密钥...");

            // 1. 解析私钥
            // 电脑生成的私钥通常是 PKCS#8 格式，我们需要用 KeyFactory 还原
            // 如果你的 key 是 openssh 格式（开头是 -----BEGIN OPENSSH PRIVATE KEY-----），
            // 那就需要转换一下。但通常安卓 sdk 生成的都是标准的 RSA。

            // 为了防止换行符干扰，先清洗一下
            String cleanKey = PRIVATE_KEY_BASE64
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.decode(cleanKey, Base64.DEFAULT);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(spec);

            // 2. 准备公钥
            // 直接用字符串的字节数组，不用算，绝对错不了
            byte[] rawBytes = PUBLIC_KEY_STRING.getBytes(StandardCharsets.UTF_8);            byte[] publicKeyBytes = new byte[rawBytes.length + 1];
            System.arraycopy(rawBytes, 0, publicKeyBytes, 0, rawBytes.length);
            publicKeyBytes[rawBytes.length] = 0; // 必须是 0，不能是字符串 "\0"
            Log.i(TAG, "静态密钥加载成功！");
            return new AdbKeyPair(privateKey, publicKeyBytes);

        } catch (Exception e) {
            Log.e(TAG, "静态密钥加载失败！请检查字符串是否复制正确", e);
            // 如果静态加载失败，返回 null 或者抛出异常，不要去生成新的，否则又乱了
            throw new RuntimeException("静态密钥配置错误");
        }
    }*/
}