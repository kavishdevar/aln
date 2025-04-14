#include "BluetoothMonitor.h"
#include <QDebug>

BluetoothMonitor::BluetoothMonitor(QObject *parent) 
    : QObject(parent), m_dbus(QDBusConnection::systemBus())
{
    if (!m_dbus.isConnected())
    {
        qWarning() << "Failed to connect to system D-Bus";
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
        qWarning() << "Failed to connect to D-Bus PropertiesChanged signal";
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
        QDBusReply<QVariant> reply = deviceInterface.call("Get", "org.bluez.Device1", "Address");

        if (reply.isValid())
        {
            QString macAddress = reply.value().toString();
            if (connected)
            {
                emit deviceConnected(macAddress);
                qDebug() << "Device connected:" << macAddress;
            }
            else
            {
                emit deviceDisconnected(macAddress);
                qDebug() << "Device disconnected:" << macAddress;
            }
        }
    }
}