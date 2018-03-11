// myudp.cpp

#include "myserver.h"

MyServer::MyServer(QObject *parent) :
    QObject(parent)
{
    // create a QUDP socket
    mySocket = new QUdpSocket(this);

    // The most common way to use QUdpSocket class is
    // to bind to an address and port using bind()
    // bool QAbstractSocket::bind(const QHostAddress & address,
    //     quint16 port = 0, BindMode mode = DefaultForPlatform)
    mySocket->bind(QHostAddress::Any, 1234);
    connect(mySocket, SIGNAL(readyRead()), this, SLOT(readyRead()));
    publicKey = 321;
}



void MyServer::readyRead()
{

    // when data comes in
    QByteArray buffer;
    buffer.resize(mySocket->pendingDatagramSize());
    QHostAddress sender;
    quint16 senderPort;
    // qint64 QUdpSocket::readDatagram(char * data, qint64 maxSize,
    //                 QHostAddress * address = 0, quint16 * port = 0)
    // Receives a datagram no larger than maxSize bytes and stores it in data.
    // The sender's host address and port is stored in *address and *port
    // (unless the pointers are 0).

    mySocket->readDatagram(buffer.data(), buffer.size(),
                         &sender, &senderPort);
   if(buffer[0]=='0')
       handshake(buffer,sender,senderPort);
    qDebug() << "Message from: " << sender.toString();
    qDebug() << "Message port: " << senderPort;
    qDebug() << "Message: " << (buffer.remove(0,1));
    buffer.clear();

}
void MyServer::handshake(QString str,QHostAddress sender,quint16 senderPort)
{
    str.remove(0,1);//delete the first symbol '0'
 if(str=="NewClient")
 {
     QByteArray Data;
     Data.push_back('0');
     Data.push_back('i');
     Data.push_back(',');
     int i = createIdentificator();
     Data.push_back(QByteArray::number(i));
     for(int i =0; i<3; i++)
        mySocket->writeDatagram(Data, sender, senderPort);
     Data.clear();
}
 else
 {
     try{
     QStringList strings = str.split(' ');
     QStringList paramId = strings[0].split(',');
     QStringList paramKey = strings[1].split(',');
        if(paramId[0]=="i"&&paramKey[0]=="k")
        {
            //TODO:modificate public key firstly
            QString key  = paramKey[1];
            QString ip = giveIPAddress();
            clients.insert(paramId[1].toInt(), Client(key,ip));
            QByteArray Data = buildParameters(ip);
            for(int i =0; i<3; i++)
               mySocket->writeDatagram(Data, sender, senderPort);

        }
     }
     catch(std::runtime_error& ex)
     {

         qDebug()<<ex.what();
     }
 }
}
int MyServer::createIdentificator()
{
return 123;
}
QString MyServer::giveIPAddress()
{
    //return random ip
    return "192.168.0.103";
}

QByteArray MyServer:: buildParameters(QString ipAddress)
{
    /* "  -m <MTU> for the maximum transmission unit\n"
               "  -a <address> <prefix-length> for the private address\n"
               "  -r <address> <prefix-length> for the forwarding route\n"
               "  -d <address> for the domain name server\n"
               "  -s <domain> for the search domain\n"*/
    //

   short mtu = 1500;
   QByteArray route ="0.0.0.0";
   QByteArray dns ="8.8.8.8";

 QByteArray parametres;
 parametres.push_back('0');
 parametres.push_back('p');
 parametres.push_back("m,");
 parametres.push_back(QByteArray::number(mtu));
 parametres.push_back(" ");
 parametres.push_back("a,");
 parametres.push_back(ipAddress.toUtf8());
 parametres.push_back(",24");
 parametres.push_back(" ");
 parametres.push_back("r,");
 parametres.push_back(route);
 parametres.push_back(",0");
 parametres.push_back(" ");
 parametres.push_back("d,");
 parametres.push_back(dns);
 parametres.push_back(" ");
// parametres.push_back("s,");parametres.push_back(" ");
 //Now we just return empty publickey
 parametres.push_back("k,");parametres.push_back(QByteArray::number(publicKey));

 return parametres;

}
