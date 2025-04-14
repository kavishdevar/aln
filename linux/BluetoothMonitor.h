#ifndef BLUETOOTHMONITOR_H
#define BLUETOOTHMONITOR_H

#include <QObject>
#include <QtDBus/QtDBus>

class BluetoothMonitor : public QObject, protected QDBusContext
{
    Q_OBJECT
public:
    explicit BluetoothMonitor(QObject *parent = nullptr);
    ~BluetoothMonitor();

signals:
    void deviceConnected(const QString &macAddress);
    void deviceDisconnected(const QString &macAddress);

private slots:
    void onPropertiesChanged(const QString &interface, const QVariantMap &changedProps, const QStringList &invalidatedProps);

private:
    QDBusConnection m_dbus;
    void registerDBusService();
};

#endif // BLUETOOTHMONITOR_H