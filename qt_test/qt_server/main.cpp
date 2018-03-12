#include <QCoreApplication>
#include "myserver.h"

int main(int argc, char *argv[])
{
    QCoreApplication a(argc, argv);

    MyServer client;
   /*QByteArray b;
    b.push_back(QByteArray::number(0));
    bool flag =false;

    b.push_back(char(0));
    if(int(b[1])==0)
    {
        b.push_back("a");
    }*/
    return a.exec();
}
