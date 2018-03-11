
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

struct Client
{
    QString publicKey;
    QString ipAddress;
    Client(QString pKey,QString ip)
    {
    publicKey = pKey;
    ipAddress = ip;
    }
};

class MyServer : public QObject
{
    Q_OBJECT
public:
    explicit MyServer(QObject *parent = 0);

     QByteArray buildParameters(QString ipAddress);
     int run (int argc,char**argv);
     int createIdentificator();
     QString giveIPAddress();
     void handshake(QString str,QHostAddress sender,quint16 senderPort);
signals:

public slots:
    void readyRead();
private:

    QUdpSocket *mySocket;
    QMap<int,Client> clients;
    int publicKey;
};

#endif // MYSERVER_H
