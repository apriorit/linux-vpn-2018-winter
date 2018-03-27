#include "ipmanager.h"
#include <QDebug>

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
    if (this->isEmpty())
        throw std::out_of_range("Queue is empty");
    return QString::fromStdString(ipPool.dequeue());
}
void IpManager::returnIPAddress(std::string ip)
{
    int size = ipPool.size();
    if (std::string("10.0.0.") != std::string(ip, 0, 7))
        throw std::invalid_argument("Should be 10.0.0.x");
    if (size >= 254)
        throw std::out_of_range("Queue is full");
    ipPool.enqueue(ip);
}
bool IpManager::isEmpty()
{
    return ipPool.isEmpty();
}

int IpManager::getSize()
{
    return ipPool.size();
}
