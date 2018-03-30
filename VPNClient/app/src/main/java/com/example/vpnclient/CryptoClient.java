package com.example.vpnclient;


import android.util.Log;

import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import java.security.SecureRandom;

public class CryptoClient {
    private SecretKey mAESKey;//AES
    private Key mPublicKey;//RSA
    private Key mPrivateKey;//RSA
    private PublicKey mServerPublicKey;//RSA

    public void generateKeys()
    {
        try {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgen.init(128);
             mAESKey = kgen.generateKey();
        }catch (Exception e) {
            Log.e("Crypto", "AES secret key error");
        }
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.genKeyPair();
            mPublicKey = kp.getPublic();
            mPrivateKey = kp.getPrivate();
        } catch (Exception e) {
            Log.e("Crypto", "RSA key pair error");
        }

    }
    public void setServerPublicRSAKey(byte[] bytes)
    {
        X509EncodedKeySpec ks = new X509EncodedKeySpec(bytes);
        KeyFactory kf = null;
        try {
            kf = KeyFactory.getInstance("RSA");

            mServerPublicKey = kf.generatePublic(ks);
        } catch (Exception e) {
        Log.e("Crypto", "RSA loading key error");
    }
    }
    public byte[] getPublicRSAKey()
    {
        return mPublicKey.getEncoded();
    }

    public byte[] encryptAES (byte[] plainText)
    {
        byte[] resByte = null;
        try {
            byte[] iv = new byte[16];
            SecureRandom srandom  = new SecureRandom();
            srandom.nextBytes(iv);
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            byte[] encodedBytes = null;
            Cipher c = Cipher.getInstance("AES/CFB8/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, mAESKey,ivspec);
            encodedBytes = c.doFinal(plainText);
            resByte  = new byte[iv.length + encodedBytes.length];
            System.arraycopy(iv, 0, resByte, 0, iv.length);
            System.arraycopy(encodedBytes, 0, resByte, iv.length, encodedBytes.length);
        } catch (Exception e) {
            Log.e("Crypto", "AES encryption error");
        }
        return resByte;

    }
    public  byte[] decryptAES (byte[] encryptBytes)
             {

        //Преобразование входных данных в массивы байт
        byte[] resultBytes = null;
        try {

            final byte[] ivBytes = new byte[16];
            System.arraycopy(encryptBytes, 0, ivBytes, 0, ivBytes.length);
            final byte [] encryptText = new byte[encryptBytes.length-16];
            System.arraycopy(encryptBytes, 16, encryptText, 0, encryptText.length);

         //   final byte[] encryptedBytes = Base64.decode(encrypted.substring(16,encrypted.length()), Base64.DEFAULT);
            //Инициализация и задание параметров расшифровки
            Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, mAESKey, new IvParameterSpec(ivBytes));
            //Расшифровка
             resultBytes = cipher.doFinal(encryptText);

        } catch (Exception e) {
            Log.e("Crypto", "AES decryption error");
        }
        return resultBytes;
    }
    public byte[] getAESKey()
    {
        return mAESKey.getEncoded();
    }
    public byte[] ecryptRSA(byte[] plainText) {
        try {
            Cipher c =  Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");;
            c.init(Cipher.ENCRYPT_MODE, mServerPublicKey);
            return c.doFinal(plainText,0,plainText.length);
        } catch (Exception e) {
            Log.e("Crypto", "RSA encryption error");
        }
        // convert to String Base64.encodeToString(encodedBytes, Base64.DEFAULT));
        return null;
    }
    public byte [] decryptRSA(byte[] encryptText)
    {
        byte[] decodedBytes = null;
        try {
            Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            c.init(Cipher.DECRYPT_MODE, mPrivateKey);
            decodedBytes = c.doFinal(encryptText);
        } catch (Exception e) {
            Log.e("Crypto", "RSA decryption error");
        }

         //to string: new String(decodedBytes)
        return decodedBytes;
    }
    public PublicKey getServerPublicRSAKey()
    {
        return mServerPublicKey;
    }
}
