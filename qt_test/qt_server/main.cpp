#include <QCoreApplication>
#include "myserver.h"

int main(int argc, char *argv[])
{
   // sudo nano /proc/sys/kernel/yama/ptrace_scope 1->0
    QCoreApplication a(argc, argv);

    MyServer client;
    return a.exec();
}
