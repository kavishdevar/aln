#include "BluetoothMonitor.h"
#include "logger.h"

#include <QDebug>

BluetoothMonitor::BluetoothMonitor(QObject *parent) 
    : QObject(parent), m_dbus(QDBusConnection::systemBus())
{
    if (!m_dbus.isConnected())
    {
        LOG_WARN("Failed to connect to system D-Bus");
        return;
    }

    registerDBusService();
}

BluetoothMonitor::~BluetoothMonitor()
{
    m_dbus.disconnectFromBus(m_dbus.name());
}

void BluetoothMonitor::registerDBusService()
{
    // Match signals for PropertiesChanged on any BlueZ Device interface
    QString matchRule = QStringLiteral("type='signal',"
                                       "interface='org.freedesktop.DBus.Properties',"
                                       "member='PropertiesChanged',"
                                       "path_namespace='/org/bluez'");

    m_dbus.connect("org.freedesktop.DBus",
                   "/org/freedesktop/DBus",
                   "org.freedesktop.DBus",
                   "AddMatch",
                   this,
                   SLOT(onPropertiesChanged(QString, QVariantMap, QStringList)));

    if (!m_dbus.connect("", "", "org.freedesktop.DBus.Properties", "PropertiesChanged",
                        this, SLOT(onPropertiesChanged(QString, QVariantMap, QStringList))))
    {
        LOG_WARN("Failed to connect to D-Bus PropertiesChanged signal");
    }
}

void BluetoothMonitor::onPropertiesChanged(const QString &interface, const QVariantMap &changedProps, const QStringList &invalidatedProps)
{
    Q_UNUSED(invalidatedProps);

    if (interface != "org.bluez.Device1")
    {
        return;
    }

    if (changedProps.contains("Connected"))
    {
        bool connected = changedProps["Connected"].toBool();
        QString path = QDBusContext::message().path();

        QDBusInterface deviceInterface("org.bluez", path, "org.freedesktop.DBus.Properties", m_dbus);
        
        // Get the device address
        QDBusReply<QVariant> addrReply = deviceInterface.call("Get", "org.bluez.Device1", "Address");
        if (!addrReply.isValid())
        {
            return;
        }
        QString macAddress = addrReply.value().toString();

        // Get UUIDs to check if it's an AirPods device
        QDBusReply<QVariant> uuidsReply = deviceInterface.call("Get", "org.bluez.Device1", "UUIDs");
        if (!uuidsReply.isValid())
        {
            return;
        }

        QStringList uuids = uuidsReply.value().toStringList();
        if (!uuids.contains("74ec2172-0bad-4d01-8f77-997b2be0722a"))
        {
            return; // Not an AirPods device
        }

        if (connected)
        {
            emit deviceConnected(macAddress);
            LOG_DEBUG("AirPods device connected:" << macAddress);
        }
        else
        {
            emit deviceDisconnected(macAddress);
            LOG_DEBUG("AirPods device disconnected:" << macAddress);
        }
    }
}