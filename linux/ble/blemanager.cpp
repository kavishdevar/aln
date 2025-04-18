#include "blemanager.h"
#include <QDebug>
#include <QTimer>

BleManager::BleManager(QObject *parent) : QObject(parent)
{
    discoveryAgent = new QBluetoothDeviceDiscoveryAgent(this);
    discoveryAgent->setLowEnergyDiscoveryTimeout(0); // Continuous scanning

    connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::deviceDiscovered,
            this, &BleManager::onDeviceDiscovered);
    connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::finished,
            this, &BleManager::onScanFinished);
    connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::errorOccurred,
            this, &BleManager::onErrorOccurred);

    // Set up pruning timer
    pruneTimer = new QTimer(this);
    connect(pruneTimer, &QTimer::timeout, this, &BleManager::pruneOldDevices);
    pruneTimer->start(PRUNE_INTERVAL_MS); // Start timer (runs every 5 seconds)
}

BleManager::~BleManager()
{
    delete discoveryAgent;
    delete pruneTimer;
}

void BleManager::startScan()
{
    qDebug() << "Starting BLE scan...";
    devices.clear();
    discoveryAgent->start(QBluetoothDeviceDiscoveryAgent::LowEnergyMethod);
    pruneTimer->start(PRUNE_INTERVAL_MS); // Ensure timer is running
}

void BleManager::stopScan()
{
    qDebug() << "Stopping BLE scan...";
    discoveryAgent->stop();
}

const QMap<QString, DeviceInfo> &BleManager::getDevices() const
{
    return devices;
}

void BleManager::onDeviceDiscovered(const QBluetoothDeviceInfo &info)
{
    if (info.manufacturerData().contains(0x004C)) // Apple manufacturer ID
    {
        QByteArray data = info.manufacturerData().value(0x004C);
        QString address = info.address().toString();
        DeviceInfo deviceInfo;
        deviceInfo.name = info.name();
        deviceInfo.address = address;
        deviceInfo.rawData = data;

        // Try to parse as Proximity message
        if (data.size() >= 1 && data[0] == 0x07 && ProximityParser::parseAppleProximityData(data, deviceInfo))
        {
            deviceInfo.deviceType = DeviceInfo::DeviceType::PROXIMITY;
        }
        // Try to parse as Find My message
        else if (data.size() >= 1 && data[0] == 0x12 && ProximityParser::parseAppleFindMyData(data, deviceInfo))
        {
            deviceInfo.deviceType = DeviceInfo::DeviceType::FIND_MY;
        }
        else
        {
            return; // Not a message we can parse
        }

        // Update timestamp
        deviceInfo.lastSeen = QDateTime::currentDateTime();

        // Store device info in the map
        devices[address] = deviceInfo;

        // Debug output
        if (deviceInfo.deviceType == DeviceInfo::DeviceType::PROXIMITY)
        {
            qDebug() << "Found proximity device:" << deviceInfo.name
                     << "Left:" << (deviceInfo.leftPodBattery >= 0 ? QString("%1%").arg(deviceInfo.leftPodBattery) : "N/A")
                     << "Right:" << (deviceInfo.rightPodBattery >= 0 ? QString("%1%").arg(deviceInfo.rightPodBattery) : "N/A")
                     << "Case:" << (deviceInfo.caseBattery >= 0 ? QString("%1%").arg(deviceInfo.caseBattery) : "N/A");
        }
        else if (deviceInfo.deviceType == DeviceInfo::DeviceType::FIND_MY)
        {
            qDebug() << "Found Find My device:" << deviceInfo.name
                     << "Maintained:" << deviceInfo.isMaintained
                     << "Battery:" << static_cast<int>(deviceInfo.batteryLevel);
        }
    }
    // Add other manufacturer checks here
}

void BleManager::onScanFinished()
{
    qDebug() << "Scan finished.";
    if (discoveryAgent->isActive())
    {
        discoveryAgent->start(QBluetoothDeviceDiscoveryAgent::LowEnergyMethod);
    }
}

void BleManager::onErrorOccurred(QBluetoothDeviceDiscoveryAgent::Error error)
{
    qDebug() << "Error occurred:" << error;
    stopScan();
}

void BleManager::pruneOldDevices()
{
    QDateTime now = QDateTime::currentDateTime();
    auto it = devices.begin();
    while (it != devices.end())
    {
        if (it.value().lastSeen.msecsTo(now) > DEVICE_TIMEOUT_MS)
        {
            qDebug() << "Removing old device:" << it.value().name << "at" << it.key();
            it = devices.erase(it); // Remove device if not seen recently
        }
        else
        {
            ++it;
        }
    }
}