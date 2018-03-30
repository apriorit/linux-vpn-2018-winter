package com.example.vpnclient;
import org.junit.Test;

import java.security.PublicKey;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void aes_isCorrect() throws Exception {
        CryptoClient testCrypto = new CryptoClient();
        testCrypto.generateKeys();
        byte[] plainText = "Hello!This is aes!".getBytes();
        byte[] encryptText = testCrypto.encryptAES(plainText);
        byte[] decryptText = testCrypto.decryptAES(encryptText);
        assertEquals(new String(plainText), new String(decryptText));
    }
    @Test
    public void aes_emptyString() throws Exception {
        CryptoClient testCrypto = new CryptoClient();
        testCrypto.generateKeys();
        byte[] plainText = "".getBytes();
        byte[] encryptText = testCrypto.encryptAES(plainText);
        byte[] decryptText = testCrypto.decryptAES(encryptText);
        assertEquals(new String(plainText), new String(decryptText));
    }
    @Test
    public void aes_multilineString() throws Exception {
        CryptoClient testCrypto = new CryptoClient();
        testCrypto.generateKeys();
        byte[] plainText = "Hello\n100190\nThis is aes._=~!\n".getBytes();
        byte[] encryptText = testCrypto.encryptAES(plainText);
        byte[] decryptText = testCrypto.decryptAES(encryptText);
        assertEquals(new String(plainText), new String(decryptText));
    }
    @Test
    public void rsa_loadingKey() throws Exception {
        CryptoClient testServer = new CryptoClient();
        CryptoClient testClient = new CryptoClient();
        testServer.generateKeys();
        testClient.generateKeys();
        testClient.setServerPublicRSAKey(testServer.getPublicRSAKey());
        assertEquals(new String(testClient.getServerPublicRSAKey().getEncoded()), new String(testServer.getPublicRSAKey()));
    }
    @Test
    public void rsa_isCorrect() throws Exception {
        CryptoClient testServer = new CryptoClient();
        CryptoClient testClient = new CryptoClient();
        testServer.generateKeys();
        testClient.generateKeys();
        testClient.setServerPublicRSAKey(testServer.getPublicRSAKey());
        byte[] plainText = "This is testing RSA!...".getBytes();
        byte[] encryptText = testClient.ecryptRSA(plainText);
        byte[] decryptText = testServer.decryptRSA(encryptText);
        assertEquals(new String(plainText), new String(decryptText));
    }
    @Test
    public void rsa_emptyString() throws Exception {
        CryptoClient testServer = new CryptoClient();
        CryptoClient testClient = new CryptoClient();
        testServer.generateKeys();
        testClient.generateKeys();
        testClient.setServerPublicRSAKey(testServer.getPublicRSAKey());
        byte[] plainText = "".getBytes();
        byte[] encryptText = testClient.ecryptRSA(plainText);
        byte[] decryptText = testServer.decryptRSA(encryptText);
        assertEquals(new String(plainText), new String(decryptText));
    }
    @Test
    public void rsa_multilineString() throws Exception {
        CryptoClient testServer = new CryptoClient();
        CryptoClient testClient = new CryptoClient();
        testServer.generateKeys();
        testClient.generateKeys();
        testClient.setServerPublicRSAKey(testServer.getPublicRSAKey());
        byte[] plainText = "Hello\n100190\nThis is rsa._=~!\n".getBytes();
        byte[] encryptText = testClient.ecryptRSA(plainText);
        byte[] decryptText = testServer.decryptRSA(encryptText);
        assertEquals(new String(plainText), new String(decryptText));
    }
}