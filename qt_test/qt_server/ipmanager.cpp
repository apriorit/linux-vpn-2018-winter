#include "ipmanager.h"

IpManager::IpManager()
{
    int j = 2;
    for (int i = 0; i < 254; i++)
    {
        ipPool.enqueue("10.0.0." + std::to_string(j));
        j++;
    }
}
QString IpManager::giveIPAddress()
{
    return QString::fromStdString(ipPool.dequeue());
}
void IpManager::returnIPAddress(std::string ip)
{
    ipPool.enqueue(ip);
}
