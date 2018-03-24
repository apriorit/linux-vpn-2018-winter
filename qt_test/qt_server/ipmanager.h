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
    bool isEmpty();
private:
    QQueue<std::string> ipPool;

};

#endif // IPMANAGER_H
