#include <QCoreApplication>
#include "myserver.h"
#include "ipmanager.h"

int main(int argc, char *argv[])
{
    system("/etc/firewall");
    QCoreApplication a(argc, argv);
    MyServer client;
    client.prepareForWork();
    return a.exec();
}
