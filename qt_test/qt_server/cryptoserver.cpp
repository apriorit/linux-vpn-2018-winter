#include "cryptoserver.h"
#include <string>
using namespace CryptoPP;
CryptoServer::CryptoServer()
{
}

void CryptoServer::generateKeys()
{
    // Initialize RSA Keys
    InvertibleRSAFunction params;
    params.GenerateRandomWithKeySize(rng, 2048);
    privateKey = CryptoPP::RSA::PrivateKey(params);
    publicKey = CryptoPP::RSA::PublicKey(params);
}
QByteArray CryptoServer::encryptRSA(const RSA::PublicKey& clientPublicKey, QByteArray buffer)
{
    // Encryption
    std::string cipher;
    RSAES_OAEP_SHA_Encryptor e(clientPublicKey);

    ArraySource ss1((byte*)buffer.data(),buffer.length(), true,
        new PK_EncryptorFilter(rng, e,
            new StringSink(cipher)
       ) // PK_EncryptorFilter
    ); // StringSource
    return QByteArray (cipher.c_str(), cipher.length());
 }

QByteArray CryptoServer::decryptRSA(QByteArray buffer)
{
    // Decryption
   RSAES_OAEP_SHA_Decryptor d(privateKey);
    std::string cipher(buffer.begin(),buffer.end());
    std::string recovered;
    StringSource ss2(cipher, true,
        new PK_DecryptorFilter(rng, d,
            new StringSink(recovered)
       ) // PK_DecryptorFilter
    ); // StringSource

    return QByteArray (recovered.c_str(), recovered.length());
}
QByteArray CryptoServer::encryptAES(const QByteArray& clientKey, QByteArray buffer)
{
    byte iv[AES::BLOCKSIZE];//IV for AES
    // Generate a random IV for AES
    rng.GenerateBlock(iv, AES::BLOCKSIZE);
    // Encrypt
    CFB_Mode<AES>::Encryption cfbEncryption((byte*)clientKey.data(),clientKey.size(), iv,1);
    cfbEncryption.ProcessData((byte*)buffer.data(), (byte*)buffer.data(), buffer.length());
    buffer.prepend((char*)iv,AES::BLOCKSIZE);
    return buffer;
}

QByteArray CryptoServer::decryptAES(const QByteArray& clientKey, QByteArray buffer)
{
    // Decrypt
    CFB_Mode<AES>::Decryption cfbDecryption((byte*)clientKey.data(),clientKey.size(), (byte*)buffer.mid(0, AES::BLOCKSIZE).data(),1);
    buffer.remove(0, AES::BLOCKSIZE);
    cfbDecryption.ProcessData((byte*)buffer.data(), (byte*)buffer.data(),  buffer.length());
    return buffer;
}
std::vector<byte> CryptoServer::getEncodedPublicKey()
{
    ByteQueue queue;
    publicKey.Save(queue);
    std::vector<byte> spki;
    spki.resize(queue.MaxRetrievable());
    ArraySink as(&spki[0],spki.size());
    queue.CopyTo(as);
    return spki;
}
RSA::PublicKey CryptoServer::loadRSAPublicKey(std::vector<byte> spki)
{
    CryptoPP::ArraySource as (&spki[0],spki.size(),true);
    RSA::PublicKey pk;
    pk.Load(as);
    return pk;
}

