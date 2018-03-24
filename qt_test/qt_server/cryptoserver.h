#ifndef CRYPTOSERVER_H
#define CRYPTOSERVER_H
#include <cryptopp/files.h>
#include <cryptopp/modes.h>
#include <cryptopp/osrng.h>
#include <cryptopp/rsa.h>
#include <cryptopp/sha.h>
#include "QByteArray"
using namespace CryptoPP;
class CryptoServer
{
   //Rsa keys for server
   RSA::PrivateKey privateKey;
   AutoSeededRandomPool rng;
   RSA::PublicKey publicKey;

public:

    CryptoServer();
    void generateKeys();
    QByteArray encryptRSA(const RSA::PublicKey& clientPublicKey,QByteArray buffer);
    QByteArray decryptRSA(QByteArray buffer);
    QByteArray decryptAES(const QByteArray& clientKey,QByteArray buffer);
    QByteArray encryptAES(const QByteArray& clientKey,QByteArray buffer);
    std::vector<byte> getEncodedPublicKey();
    RSA::PublicKey loadRSAPublicKey(std::vector<byte> queue);

};

#endif // CRYPTOSERVER_H
