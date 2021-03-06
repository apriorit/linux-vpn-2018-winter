QT -= gui

QT += core
QT += network
TARGET = MyVpn
CONFIG += c++11 console
CONFIG -= app_bundle
LIBS += -L/usr/lib/crypto++ -lcrypto++
INCS += I/usr/include/crypto++
# The following define makes your compiler emit warnings if you use
# any feature of Qt which as been marked deprecated (the exact warnings
# depend on your compiler). Please consult the documentation of the
# deprecated API in order to know how to port your code away from it.
DEFINES += QT_DEPRECATED_WARNINGS

# You can also make your code fail to compile if you use deprecated APIs.
# In order to do so, uncomment the following line.
# You can also select to disable deprecated APIs only up to a certain version of Qt.
#DEFINES += QT_DISABLE_DEPRECATED_BEFORE=0x060000    # disables all the APIs deprecated before Qt 6.0.0
TEMPLATE = app
SOURCES += src/main.cpp \
    src/myserver.cpp \
    src/ipmanager.cpp \
    src/cryptoserver.cpp

HEADERS += \
    src/myserver.h \
    src/ipmanager.h \
    src/cryptoserver.h \
    src/Client.h

target.path = /usr/bin

INSTALLS += target
