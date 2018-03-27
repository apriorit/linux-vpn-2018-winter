#include <QtTest>
#include "../../qt_test/qt_server/ipmanager.cpp"

// add necessary includes here

class tes_ipmanager : public QObject
{
    Q_OBJECT

public:
    tes_ipmanager();
    ~tes_ipmanager();
private:
    IpManager *manager;

private slots:
    void init();
    void cleanup();
    void test_poolNumber();
    void test_poolValues();
    void test_returnInvalidIp();
    void test_giveFromEmptyPool();
    void test_returnToFullPool();

};

tes_ipmanager::tes_ipmanager()
{

}

tes_ipmanager::~tes_ipmanager()
{

}

void tes_ipmanager::init()
{
    manager = new IpManager();
}

void tes_ipmanager::cleanup()
{
    delete(manager);
}

void tes_ipmanager::test_poolNumber()
{
    QCOMPARE(manager->getSize(), 254);
}

void tes_ipmanager::test_poolValues()
{
    int j = 2;
    std::string str ("10.0.0.");
    for (int i = 0; i < manager->getSize(); i++)
    {
        QCOMPARE(std::string(manager->giveIPAddress().toUtf8()), str + std::to_string(j));
        j++;
    }
}
void tes_ipmanager::test_returnInvalidIp()
{
    manager->giveIPAddress();
    QVERIFY_EXCEPTION_THROWN(manager->returnIPAddress("192.168.0.1"), std::invalid_argument);
}

void tes_ipmanager::test_giveFromEmptyPool()
{
    int size = manager->getSize();
    for (int i = 0; i < size; i++)
    {
        manager->giveIPAddress();
    }
    QVERIFY_EXCEPTION_THROWN(manager->giveIPAddress(), std::out_of_range);
}
void tes_ipmanager::test_returnToFullPool()
{
    QVERIFY_EXCEPTION_THROWN(manager->returnIPAddress("10.0.0.28"), std::out_of_range);
}

QTEST_APPLESS_MAIN(tes_ipmanager)

#include "tst_tes_ipmanager.moc"
