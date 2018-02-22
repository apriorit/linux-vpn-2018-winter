TEMPLATE = app
CONFIG += console c++11
CONFIG -= app_bundle
CONFIG -= qt

SOURCES += main.cpp
LIBS += -L/usr/lib/x86_64-linux-gnu -lboost_system
