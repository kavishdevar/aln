#pragma once

#include <QObject>
#include <QSystemTrayIcon>
#include <QMenu>
#include <QBluetoothDeviceDiscoveryAgent>
#include <QBluetoothSocket>
#include <QDBusInterface>
#include "BluetoothHandler.h"

class AirPodsTrayApp : public QObject {
    Q_OBJECT

public:
    AirPodsTrayApp();

public slots:
    void connectToDevice(const QString &address);
    void showAvailableDevices();
    void setNoiseControlMode(int mode);
    void setConversationalAwareness(bool enabled);
    void updateNoiseControlMenu(int mode);
    void updateBatteryTooltip(const QString &status);
    void updateTrayIcon(const QString &status);
    void handleEarDetection(const QString &status);

private slots:
    void onTrayIconActivated(QSystemTrayIcon::ActivationReason reason);
    void onDeviceDiscovered(const QBluetoothDeviceInfo &device);
    void onDiscoveryFinished();
    void onDeviceConnected(const QBluetoothAddress &address);
    void onDeviceDisconnected(const QBluetoothAddress &address);
    void onPhoneDataReceived();

signals:
    void noiseControlModeChanged(int mode);
    void earDetectionStatusChanged(const QString &status);
    void batteryStatusChanged(const QString &status);

private:
    void initializeMprisInterface();
    void connectToPhone();
    void relayPacketToPhone(const QByteArray &packet);
    void handlePhonePacket(const QByteArray &packet);

    QSystemTrayIcon *trayIcon;
    QMenu *trayMenu;
    QBluetoothDeviceDiscoveryAgent *discoveryAgent;
    QBluetoothSocket *socket = nullptr;
    QBluetoothSocket *phoneSocket = nullptr;
    QDBusInterface *mprisInterface;
    QString connectedDeviceMacAddress;
};
