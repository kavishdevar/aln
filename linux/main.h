#ifndef MAIN_H
#define MAIN_H

#include <QApplication>
#include <QQmlApplicationEngine>
#include <QSystemTrayIcon>
#include <QMenu>
#include <QAction>
#include <QActionGroup>
#include <QBluetoothDeviceDiscoveryAgent>
#include <QBluetoothLocalDevice>
#include <QBluetoothSocket>
#include <QQuickWindow>
#include <QDebug>
#include <QInputDialog>
#include <QQmlContext>
#include <QLoggingCategory>
#include <QThread>
#include <QTimer>
#include <QPainter>
#include <QPalette>
#include <QDBusInterface>
#include <QDBusReply>
#include <QDBusConnectionInterface>
#include <QProcess>
#include <QRegularExpression>
#include <QFile>
#include <QTextStream>
#include <QStandardPaths>

Q_LOGGING_CATEGORY(airpodsApp, "airpodsApp")

#define LOG_INFO(msg) qCInfo(airpodsApp) << "\033[32m" << msg << "\033[0m"
#define LOG_WARN(msg) qCWarning(airpodsApp) << "\033[33m" << msg << "\033[0m"
#define LOG_ERROR(msg) qCCritical(airpodsApp) << "\033[31m" << msg << "\033[0m"
#define LOG_DEBUG(msg) qCDebug(airpodsApp) << "\033[34m" << msg << "\033[0m"

#define PHONE_MAC_ADDRESS "22:22:F5:BB:1C:A0"

#define MANUFACTURER_ID 0x1234
#define MANUFACTURER_DATA "ALN_AirPods"

#endif
