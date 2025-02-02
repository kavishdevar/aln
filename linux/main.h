#ifndef MAIN_H
#define MAIN_H

#include <QAction>
#include <QActionGroup>
#include <QApplication>
#include <QBluetoothDeviceDiscoveryAgent>
#include <QBluetoothLocalDevice>
#include <QBluetoothServer>
#include <QBluetoothSocket>
#include <QBluetoothUuid>
#include <QDBusConnection>
#include <QDBusConnectionInterface>
#include <QDBusInterface>
#include <QDBusMessage>
#include <QDBusPendingCallWatcher>
#include <QDBusReply>
#include <QDebug>
#include <QFile>
#include <QInputDialog>
#include <QLoggingCategory>
#include <QMenu>
#include <QPainter>
#include <QPalette>
#include <QProcess>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickWindow>
#include <QRegularExpression>
#include <QStandardPaths>
#include <QSystemTrayIcon>
#include <QTextStream>
#include <QThread>
#include <QTimer>

Q_LOGGING_CATEGORY(airpodsApp, "airpodsApp")

#define LOG_INFO(msg) qCInfo(airpodsApp) << "\033[32m" << msg << "\033[0m"
#define LOG_WARN(msg) qCWarning(airpodsApp) << "\033[33m" << msg << "\033[0m"
#define LOG_ERROR(msg) qCCritical(airpodsApp) << "\033[31m" << msg << "\033[0m"
#define LOG_DEBUG(msg) qCDebug(airpodsApp) << "\033[34m" << msg << "\033[0m"

#define PHONE_MAC_ADDRESS "22:22:F5:BB:1C:A0"

#define MANUFACTURER_ID 0x1234
#define MANUFACTURER_DATA "ALN_AirPods"

#endif
