#ifndef CLIENT_H
#define CLIENT_H
#include <QTimer>
#include "myserver.h"
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
        qDebug()<<errno;
        timer = new QTimer();
        qDebug()<<errno;
        m_port = port;
    }


    void setAesKey(const QByteArray key)
    {
        aesKey = key;
    }
};
#endif // CLIENT_H
