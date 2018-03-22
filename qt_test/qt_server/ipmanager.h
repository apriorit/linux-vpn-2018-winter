#ifndef IPMANAGER_H
#define IPMANAGER_H
#include <QQueue>
#include <QString>

class IpManager
{
public:
    IpManager();

    QString giveIPAddress();
    void returnIPAddress(std::string ip);
private:
    QQueue<std::string> ipPool;

};

#endif // IPMANAGER_H
