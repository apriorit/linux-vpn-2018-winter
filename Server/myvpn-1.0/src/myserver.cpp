// myudp.cpp

#include "myserver.h"
#include "cryptoserver.h"
MyServer::MyServer(QObject *parent) :
    QObject(parent)
{
    // create a QUDP socket
    mySocket = new QUdpSocket(this);
    manager = new IpManager();
    myCrypto = new CryptoServer();
    signalMapper = new QSignalMapper(this);

}
MyServer::~MyServer()
{
    close(interface);
    delete mySocket;
    delete myCrypto;
    delete manager;
    delete signalMapper;
}
void MyServer::prepareForWork()
{
    mySocket->bind(QHostAddress::AnyIPv4, 1234);
    connect(mySocket, SIGNAL(readyRead()), this, SLOT(readyRead()));
    interface = get_interface("tun44");
    myCrypto->generateKeys();
}
void MyServer::disconnect(QString ip)
{  
    auto it = clients.find(ip);
    if (it == clients.end())
        return;
    it->timer->stop();
    delete(it->timer);
    try {
        manager->returnIPAddress(std::string(ip.toUtf8()));
     }
    catch (std::logic_error &ex)
    {
        qDebug() << ex.what();
    }
    rclients.remove(it->realIpAddress.toString() + " " + QString::number(it->m_port));
    clients.remove(ip);
    qDebug() << "Client[" << ip << "] was disconnected";
}
QByteArray MyServer::getErrorMessage()
{
    //0Error
    QByteArray temp;
    temp.push_back(char(0));
    temp.push_back("Error");
    return temp;
}
QMap<QString,Client>::iterator MyServer::addNewClient(const CryptoPP::RSA::PublicKey& key,const QHostAddress &sender, const quint16 &senderPort)
{

    QString localIP = "";
    try {
        localIP = manager->giveIPAddress();
    }
    catch (std::out_of_range &ex)
    {
        qDebug() << ex.what();
        return NULL;
    }
    auto myClient = clients.insert(localIP, Client(key, sender, senderPort));
    rclients.insert(myClient->realIpAddress.toString() + " " + QString::number(myClient->m_port), localIP);
    signalMapper->setMapping(myClient->timer, localIP);
    //connect map object to obtain client, which was disconnected
    connect (myClient->timer, SIGNAL(timeout()), signalMapper, SLOT(map()));
    connect (signalMapper, SIGNAL(mapped(QString)), this, SLOT(disconnect(QString)));
    myClient->timer->start(20000);

    return myClient;
}
bool MyServer::clientIsRegistred(const QHostAddress& sender, const quint16& senderPort)
{
    //find global ip
    auto it = rclients.find(sender.toString() + " " + QString::number(senderPort));
    if (it != rclients.end() )
    {
        return true;
    }
    return false;
}
//Return answer on client request: "0kServerPublicKey"
QByteArray MyServer::getAnswerOnClientRequest()
{
    //0kServerPublicKey
    QByteArray msgForClient;
    msgForClient.push_back(char(0));
    msgForClient.push_back("k");
    std::vector<byte> serverKey = myCrypto->getEncodedPublicKey();
    QByteArray arr;
    arr.resize(serverKey.size());
    std::copy(serverKey.begin(),serverKey.end(),arr.begin());
    msgForClient.push_back(arr);
    return msgForClient;
}

 QMap<QString,Client>::iterator MyServer::findClientForLocalIp(const QHostAddress &sender, const quint16& senderPort)
 {
     QMap<QString,QString>::iterator it = rclients.find(sender.toString() + " " + QString::number(senderPort));
     if (it != rclients.end() )
     {
         QMap<QString,Client>::iterator it1= clients.find(it.value());
         if(it1!=clients.end())
             return it1;
     }
    return clients.end();
 }

 void MyServer::readyRead()
 {
     // when data comes in
     QByteArray buffer;
     QByteArray newbuffer;
     buffer.resize(mySocket->pendingDatagramSize());
     newbuffer.resize(1500);
     QHostAddress sender;
     quint16 senderPort;
     QString type;


     mySocket->readDatagram(buffer.data(), buffer.size(),
                          &sender, &senderPort);
     //check on length
     if(buffer.length()>0)
     {
         if(int(buffer[0])!=2)
        {
            handshake(buffer,sender,senderPort);
            type = "handshake";
        }
        else
        {
            buffer.remove(0,1);
            auto thisClient = findClientForLocalIp(sender, senderPort);
            if (thisClient == clients.end()) {
                qDebug() << "Error\n";
                exit(0);
             }
            //Decrypt message
            buffer = myCrypto->decryptAES(thisClient->aesKey,buffer);
            int wCount = 0;
            int rCount = 0;
            wCount = write(interface, buffer, buffer.size());
            rCount = read(interface, newbuffer.data(), newbuffer.size());
            qDebug() << "r" << rCount << " w" << wCount;

            if (rCount > 0)
            {
                struct iphdr *ip  = reinterpret_cast<iphdr*>(newbuffer.data());

                if (ip->version == 4)
                {
                    //uint32_t ip_addr = ntohl(ip->daddr);
                    char buf[24];
                    inet_ntop(AF_INET, &ip->daddr, buf, sizeof(buf));
                    QString res = QString::fromLocal8Bit(buf);
                    qDebug() << "RES:" <<  res;
                    auto it = clients.find(res);
                    if (it != clients.end())
                    {
                        //Encrypt message for client
                        newbuffer = myCrypto->encryptAES(it->aesKey,QByteArray(newbuffer,rCount));
                        newbuffer.push_front(2);
                        mySocket->writeDatagram(newbuffer, it->realIpAddress, it->m_port);
                        it->timer->start(20000);

                        qDebug() << res;
                        type = "traffic";
                    }
                    else
                    {
                        qDebug() << "No user with this IP";
                    }

                }
                else
                {

                    qDebug() << "non IPv4 packet";
                }
            }
        }
         qDebug() << sender << "  " << senderPort << "  " << type;

     }

 }

 void MyServer::handshake(QByteArray msg,QHostAddress sender,quint16 senderPort)
 {
  int ch = int(msg[0]);
  msg.remove(0,1);
  switch(ch)
  {
     case 0://Request for new client
     {
          if(msg.startsWith("NewClient"))
          {
              if(manager->isEmpty())
                  mySocket->writeDatagram(getErrorMessage(), sender, senderPort);
                  else
                  {
                      msg.remove(0,9);
                      if(!clientIsRegistred(sender,senderPort))
                      {
                          std::vector<byte> v;
                          v.resize(msg.size());
                          std::copy(msg.begin(),msg.end(),v.begin());
                          CryptoPP::RSA::PublicKey pk = myCrypto->loadRSAPublicKey(v);
                          addNewClient(pk,sender,senderPort);
                          //send server Key
                           mySocket->writeDatagram(getAnswerOnClientRequest(), sender, senderPort);
                             qDebug()<<msg;
                      }
                  }
          }

          break;
     }
     case 1:
     {
       //if client is registred, set aes key
      if(msg.startsWith("aes")&&clientIsRegistred(sender,senderPort))
         {
            msg.remove(0,3);
            QString address = rclients[sender.toString() + " " + QString::number(senderPort)];
            QMap<QString,Client>::iterator it = clients.find(address);
            it->setAesKey(myCrypto->decryptRSA(msg));
            QByteArray params = myCrypto->encryptRSA(it->publicKey,buildParameters(address));
            params.push_front('p');
            params.push_front(char(1));
           //send params to client
             mySocket->writeDatagram(params, it->realIpAddress, it->m_port);
             qDebug()<<params;
           }
      break;
     }
     case 3:
     {
         if(msg.startsWith("Bye"))
         {
             //проверить существует ли такой клиент, и если существует делаем ему disconnect
             auto it = rclients.find(sender.toString() + " " + QString::number(senderPort));
             if (it != rclients.end()) {
                 disconnect(it.value());
                 qDebug()<<"Client disconnect" ;
             }
         }
         break;
      }
     default:
     {
      qDebug()<<"Unknown message from client";
     }
  }
 }

QByteArray MyServer:: buildParameters(QString ipAddress)
{
    /* "  -m <MTU> for the maximum transmission unit\n"
               "  -a <address> <prefix-length> for the private address\n"
               "  -r <address> <prefix-length> for the forwarding route\n"
               "  -d <address> for the domain name server\n"*/

  QByteArray params = "m,1500 r,0.0.0.0,0 d,8.8.8.8 a,";
  params.push_back(ipAddress.toUtf8());
  params.push_back(",8 ");



 return params;

}

int MyServer::get_interface(char *name)
{
    int interface = open("/dev/net/tun", O_RDWR | O_NONBLOCK);
        ifreq ifr;
        memset(&ifr, 0, sizeof(ifr));
        ifr.ifr_flags = IFF_TUN | IFF_NO_PI;
        strncpy(ifr.ifr_name, name, sizeof(ifr.ifr_name));
        if (ioctl(interface, TUNSETIFF, &ifr)) {
            perror("Cannot get TUN interface");
            exit(1);
        }
	else {
       system("ip address add 10.0.0.1/8 dev tun44");
	   system("ip link set up dev tun44");
	}
        return interface;
}

