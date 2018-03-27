#ifndef MYSERVER_H
#define MYSERVER_H
#include <QObject>
#include <QtNetwork/QUdpSocket>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <errno.h>
#include <fcntl.h>
#include <net/if.h>
#include <linux/if_tun.h>
#include "QMap"
#include <linux/ip.h>
#include <linux/ipv6.h>
#include <QQueue>
#include <QTimer>
#include <iterator>
#include <QSignalMapper>
#include <ipmanager.h>
#include "cryptoserver.h"
#include "Client.h"

class MyServer : public QObject
{
    Q_OBJECT
public:
    explicit MyServer(QObject *parent = 0);
     ~MyServer();
    void prepareForWork();
public slots:
    void readyRead();
    void disconnect(QString ip);
private:
    QUdpSocket *mySocket;
    //virtual ip , struct Client
    QMap<QString,Client> clients;
    //real ip , virtual ip
    QMap<QString, QString> rclients;
    int interface;
//    QSignalMapper* signalMapper; //advanced signal class
    QSignalMapper* signalMapper;
    IpManager *manager;
    CryptoServer* myCrypto;
private:
    QByteArray buildParameters(QString ipAddress);
    int run (int argc,char**argv);
    QString giveIPAddress();
    void handshake(QByteArray msg,QHostAddress sender,quint16 senderPort);
    int get_interface(char *name);
    QByteArray getAnswerOnClientRequest();
    bool clientIsRegistred(const QHostAddress& sender, const quint16& senderPort);
    QMap<QString,Client>::iterator addNewClient(const CryptoPP::RSA::PublicKey& key,const QHostAddress& sender, const quint16& senderPort);
    QMap<QString,Client>::iterator findClientForLocalIp(const QHostAddress& sender);
    QByteArray getErrorMessage();
};

#endif // MYSERVER_H
