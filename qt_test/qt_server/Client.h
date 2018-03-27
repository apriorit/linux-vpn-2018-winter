#ifndef CLIENT_H
#define CLIENT_H
#include <QTimer>
struct Client
{
    CryptoPP::RSA::PublicKey publicKey;
    QByteArray aesKey;
    QHostAddress realIpAddress;
    QTimer *timer;
    qint64 m_port;
    Client(const CryptoPP::RSA::PublicKey& pKey, const QHostAddress &realIP, const qint64& port)
    {
        publicKey = pKey;
        realIpAddress = realIP;
        timer = new QTimer();
        m_port = port;
    }
    ~Client()
    {
        delete timer;
    }

    void setAesKey(const QByteArray key)
    {
        aesKey = key;
    }
};
#endif // CLIENT_H
