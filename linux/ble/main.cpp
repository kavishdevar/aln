#include <QApplication>
#include <QMainWindow>
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGridLayout>
#include <QLabel>
#include <QPushButton>
#include <QTableWidget>
#include <QHeaderView>
#include <QProgressBar>
#include <QTimer>
#include <QGroupBox>
#include <QBluetoothDeviceDiscoveryAgent>
#include <QBluetoothDeviceInfo>
#include <QDebug>
#include <QMap>
#include <QSystemTrayIcon>
#include <QMenu>

class DeviceInfo
{
public:
    QString name;
    QString address;
    int leftPodBattery = 0;
    int rightPodBattery = 0;
    int caseBattery = 0;
    bool leftCharging = false;
    bool rightCharging = false;
    bool caseCharging = false;
    quint16 deviceModel = 0;
    quint8 lidOpenCounter = 0;
    quint8 deviceColor = 0;
    quint8 status = 0;
    QByteArray rawData;
};

class BleScanner : public QMainWindow
{
    Q_OBJECT

public:
    BleScanner(QWidget *parent = nullptr) : QMainWindow(parent)
    {
        setWindowTitle("AirPods Battery Monitor");
        resize(600, 400);

        // Create central widget and layout
        QWidget *centralWidget = new QWidget(this);
        QVBoxLayout *mainLayout = new QVBoxLayout(centralWidget);
        setCentralWidget(centralWidget);

        // Create scan control buttons
        QHBoxLayout *buttonLayout = new QHBoxLayout();
        scanButton = new QPushButton("Start Scan", this);
        stopButton = new QPushButton("Stop Scan", this);
        stopButton->setEnabled(false);

        buttonLayout->addWidget(scanButton);
        buttonLayout->addWidget(stopButton);
        buttonLayout->addStretch();

        mainLayout->addLayout(buttonLayout);

        // Create device table
        deviceTable = new QTableWidget(0, 5, this);
        deviceTable->setHorizontalHeaderLabels({"Device", "Left Pod", "Right Pod", "Case", "Address"});
        deviceTable->horizontalHeader()->setSectionResizeMode(QHeaderView::Stretch);
        deviceTable->setSelectionBehavior(QTableWidget::SelectRows);
        deviceTable->setEditTriggers(QTableWidget::NoEditTriggers);
        mainLayout->addWidget(deviceTable);

        // Create detail view for selected device
        detailsGroup = new QGroupBox("Device Details", this);
        QGridLayout *detailsLayout = new QGridLayout(detailsGroup);

        // Left pod details
        detailsLayout->addWidget(new QLabel("Left Pod:"), 0, 0);
        leftBatteryBar = new QProgressBar(this);
        leftBatteryBar->setRange(0, 100);
        leftBatteryBar->setTextVisible(true);
        detailsLayout->addWidget(leftBatteryBar, 0, 1);
        leftChargingLabel = new QLabel(this);
        detailsLayout->addWidget(leftChargingLabel, 0, 2);

        // Right pod details
        detailsLayout->addWidget(new QLabel("Right Pod:"), 1, 0);
        rightBatteryBar = new QProgressBar(this);
        rightBatteryBar->setRange(0, 100);
        rightBatteryBar->setTextVisible(true);
        detailsLayout->addWidget(rightBatteryBar, 1, 1);
        rightChargingLabel = new QLabel(this);
        detailsLayout->addWidget(rightChargingLabel, 1, 2);

        // Case details
        detailsLayout->addWidget(new QLabel("Case:"), 2, 0);
        caseBatteryBar = new QProgressBar(this);
        caseBatteryBar->setRange(0, 100);
        caseBatteryBar->setTextVisible(true);
        detailsLayout->addWidget(caseBatteryBar, 2, 1);
        caseChargingLabel = new QLabel(this);
        detailsLayout->addWidget(caseChargingLabel, 2, 2);

        // Additional info
        detailsLayout->addWidget(new QLabel("Model:"), 3, 0);
        modelLabel = new QLabel(this);
        detailsLayout->addWidget(modelLabel, 3, 1);

        detailsLayout->addWidget(new QLabel("Status:"), 4, 0);
        statusLabel = new QLabel(this);
        detailsLayout->addWidget(statusLabel, 4, 1);

        detailsLayout->addWidget(new QLabel("Lid Opens:"), 5, 0);
        lidOpenLabel = new QLabel(this);
        detailsLayout->addWidget(lidOpenLabel, 5, 1);

        detailsLayout->addWidget(new QLabel("Color:"), 6, 0);
        colorLabel = new QLabel(this);
        detailsLayout->addWidget(colorLabel, 6, 1);

        // Raw data display
        detailsLayout->addWidget(new QLabel("Raw Data:"), 7, 0);
        rawDataLabel = new QLabel(this);
        rawDataLabel->setWordWrap(true);
        detailsLayout->addWidget(rawDataLabel, 7, 1, 1, 2);

        mainLayout->addWidget(detailsGroup);
        detailsGroup->setVisible(false);

        // Create system tray icon
        trayIcon = new QSystemTrayIcon(QIcon::fromTheme("bluetooth"), this);
        QMenu *trayMenu = new QMenu(this);
        QAction *showAction = trayMenu->addAction("Show");
        trayMenu->addSeparator();
        QAction *exitAction = trayMenu->addAction("Exit");

        trayIcon->setContextMenu(trayMenu);
        trayIcon->setToolTip("AirPods Battery Monitor");
        trayIcon->show();

        // Create BLE discovery agent
        discoveryAgent = new QBluetoothDeviceDiscoveryAgent(this);
        discoveryAgent->setLowEnergyDiscoveryTimeout(0); // Infinite scanning

        // Setup auto-refresh timer
        refreshTimer = new QTimer(this);

        // Connect signals and slots
        connect(scanButton, &QPushButton::clicked, this, &BleScanner::startScan);
        connect(stopButton, &QPushButton::clicked, this, &BleScanner::stopScan);
        connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::deviceDiscovered,
                this, &BleScanner::onDeviceDiscovered);
        connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::finished,
                this, &BleScanner::onScanFinished);
        connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::errorOccurred,
                this, &BleScanner::onErrorOccurred);
        connect(deviceTable, &QTableWidget::itemSelectionChanged,
                this, &BleScanner::onDeviceSelected);
        connect(refreshTimer, &QTimer::timeout, this, &BleScanner::updateDeviceList);
        connect(showAction, &QAction::triggered, this, &BleScanner::show);
        connect(exitAction, &QAction::triggered, qApp, &QApplication::quit);
    }

private slots:
    void startScan()
    {
        qDebug() << "Starting BLE scan...";
        scanButton->setEnabled(false);
        stopButton->setEnabled(true);

        // Clear previous devices
        devices.clear();
        deviceTable->setRowCount(0);
        detailsGroup->setVisible(false);

        // Start discovery
        discoveryAgent->start(QBluetoothDeviceDiscoveryAgent::LowEnergyMethod);

        // Start refresh timer (update UI every 2 seconds)
        refreshTimer->start(2000);
    }

    void stopScan()
    {
        qDebug() << "Stopping BLE scan...";
        discoveryAgent->stop();
        refreshTimer->stop();
        scanButton->setEnabled(true);
        stopButton->setEnabled(false);
    }

    void onDeviceDiscovered(const QBluetoothDeviceInfo &info)
    {
        // Check if this is an Apple device with the manufacturer data we're interested in
        if (info.manufacturerData().contains(0x004C))
        {
            QByteArray data = info.manufacturerData().value(0x004C);

            // Check for proximity pairing format (byte 0 = 0x07)
            if (data.size() >= 10 && data[0] == 0x07)
            {
                QString address = info.address().toString();

                // Create or up  date device info
                DeviceInfo deviceInfo;
                deviceInfo.name = info.name().isEmpty() ? "AirPods" : info.name();
                deviceInfo.address = address;
                deviceInfo.rawData = data; // Store the raw data

                // Parse device model
                deviceInfo.deviceModel = static_cast<quint16>(data[4]) | (static_cast<quint8>(data[3]) << 8);

                // Store status byte (byte 5)
                deviceInfo.status = static_cast<quint8>(data[5]);

                // Parse pods battery levels (byte 6)
                quint8 podsBatteryByte = static_cast<quint8>(data[6]);
                deviceInfo.leftPodBattery = ((podsBatteryByte >> 4) & 0x0F) * 10; // Scale to 0-100
                deviceInfo.rightPodBattery = (podsBatteryByte & 0x0F) * 10;       // Scale to 0-100

                // Parse charging status and case battery level (byte 7)
                quint8 statusByte = static_cast<quint8>(data[7]);
                deviceInfo.caseCharging = (statusByte & 0x02) != 0;
                deviceInfo.rightCharging = (statusByte & 0x04) != 0;
                deviceInfo.leftCharging = (statusByte & 0x08) != 0;
                deviceInfo.caseBattery = ((statusByte >> 4) & 0x0F) * 10; // Scale to 0-100

                // Byte 8 is the lid open counter
                deviceInfo.lidOpenCounter = static_cast<quint8>(data[8]);

                // Byte 9 is the device color
                deviceInfo.deviceColor = static_cast<quint8>(data[9]);

                // Update device list
                devices[address] = deviceInfo;

                qDebug() << "Found device:" << deviceInfo.name
                         << "Left:" << deviceInfo.leftPodBattery << "%"
                         << "Right:" << deviceInfo.rightPodBattery << "%"
                         << "Case:" << deviceInfo.caseBattery << "%";
            }
        }
    }

    void onScanFinished()
    {
        qDebug() << "Scan finished.";
        // In case of a manual stop, we don't restart
        if (stopButton->isEnabled())
        {
            // Restart scanning to keep it continuous
            discoveryAgent->start(QBluetoothDeviceDiscoveryAgent::LowEnergyMethod);
        }
    }

    void onErrorOccurred(QBluetoothDeviceDiscoveryAgent::Error error)
    {
        qDebug() << "Error occurred:" << error;
        stopScan();
    }

    void updateDeviceList()
    {
        // Store currently selected device address
        QString selectedAddress;
        if (deviceTable->selectionModel()->hasSelection())
        {
            int row = deviceTable->selectionModel()->selectedRows().first().row();
            selectedAddress = deviceTable->item(row, 4)->text();
        }

        // Clear and repopulate the table
        deviceTable->setRowCount(0);
        deviceTable->setRowCount(devices.size());

        int row = 0;
        for (auto it = devices.begin(); it != devices.end(); ++it, ++row)
        {
            const DeviceInfo &device = it.value();

            // Device name
            QTableWidgetItem *nameItem = new QTableWidgetItem(device.name);
            deviceTable->setItem(row, 0, nameItem);

            // Left pod battery
            QString leftStatus = QString::number(device.leftPodBattery) + "%";
            if (device.leftCharging)
                leftStatus += " ⚡";
            QTableWidgetItem *leftItem = new QTableWidgetItem(leftStatus);
            deviceTable->setItem(row, 1, leftItem);

            // Right pod battery
            QString rightStatus = QString::number(device.rightPodBattery) + "%";
            if (device.rightCharging)
                rightStatus += " ⚡";
            QTableWidgetItem *rightItem = new QTableWidgetItem(rightStatus);
            deviceTable->setItem(row, 2, rightItem);

            // Case battery
            QString caseStatus = QString::number(device.caseBattery) + "%";
            if (device.caseCharging)
                caseStatus += " ⚡";
            QTableWidgetItem *caseItem = new QTableWidgetItem(caseStatus);
            deviceTable->setItem(row, 3, caseItem);

            // Address
            QTableWidgetItem *addressItem = new QTableWidgetItem(device.address);
            deviceTable->setItem(row, 4, addressItem);

            // Reselect the previously selected device
            if (device.address == selectedAddress)
            {
                deviceTable->selectRow(row);
            }
        }

        // Update system tray tooltip with device info if available
        if (!devices.isEmpty())
        {
            auto it = devices.begin();
            QString tooltip = QString("%1\nLeft: %2% | Right: %3% | Case: %4%")
                                  .arg(it.value().name)
                                  .arg(it.value().leftPodBattery)
                                  .arg(it.value().rightPodBattery)
                                  .arg(it.value().caseBattery);
            trayIcon->setToolTip(tooltip);
        }
    }

    void onDeviceSelected()
    {
        if (!deviceTable->selectionModel()->hasSelection())
        {
            detailsGroup->setVisible(false);
            return;
        }

        int row = deviceTable->selectionModel()->selectedRows().first().row();
        QString address = deviceTable->item(row, 4)->text();

        if (!devices.contains(address))
        {
            detailsGroup->setVisible(false);
            return;
        }

        const DeviceInfo &device = devices[address];

        // Update details view
        leftBatteryBar->setValue(device.leftPodBattery);
        rightBatteryBar->setValue(device.rightPodBattery);
        caseBatteryBar->setValue(device.caseBattery);

        leftChargingLabel->setText(device.leftCharging ? "Charging" : "Not Charging");
        rightChargingLabel->setText(device.rightCharging ? "Charging" : "Not Charging");
        caseChargingLabel->setText(device.caseCharging ? "Charging" : "Not Charging");

        // Set model name based on model ID
        QString modelName = getModelName(device.deviceModel);
        modelLabel->setText(modelName + " (0x" + QString::number(device.deviceModel, 16).toUpper() + ")");

        // Display status byte with binary representation
        QString statusBinary = QString("%1").arg(device.status, 8, 2, QChar('0'));
        statusLabel->setText(QString("0x%1 (%2) - Binary: %3")
                                 .arg(device.status, 2, 16, QChar('0'))
                                 .toUpper()
                                 .arg(device.status)
                                 .arg(statusBinary));

        lidOpenLabel->setText(QString::number(device.lidOpenCounter));

        // Set color name based on color ID
        QString colorName = getColorName(device.deviceColor);
        colorLabel->setText(colorName + " (" + QString::number(device.deviceColor) + ")");

        // Display raw data bytes
        QString rawDataStr = "Bytes: ";
        for (int i = 0; i < device.rawData.size(); ++i)
        {
            rawDataStr += QString("0x%1 ").arg(static_cast<quint8>(device.rawData[i]), 2, 16, QChar('0')).toUpper();
        }
        rawDataLabel->setText(rawDataStr);

        detailsGroup->setVisible(true);
    }

private:
    QString getModelName(quint16 modelId)
    {
        switch (modelId)
        {
        // AirPods
        case 0x0220:
            return "AirPods 1st Gen";
        case 0x0F20:
            return "AirPods 2nd Gen";
        case 0x1320:
            return "AirPods 3rd Gen";
        case 0x1920:
            return "AirPods 4th Gen";
        case 0x1B20:
            return "AirPods 4th Gen (ANC)";

        // AirPods Max
        case 0x0A20:
            return "AirPods Max";
        case 0x1F20:
            return "AirPods Max (USB-C)";

        // Airpods Pro
        case 0x0e20:
            return "AirPods Pro";
        case 0x1420:
            return "AirPods Pro 2nd Gen";
        case 0x2420:
            return "AirPods Pro 2nd Gen (USB-C)";
        default:
            return "Unknown Apple Device";
        }
    }

    QString getColorName(quint8 colorId)
    {
        switch (colorId)
        {
        case 0x00:
            return "White";
        case 0x01:
            return "Black";
        case 0x02:
            return "Red";
        case 0x03:
            return "Blue";
        case 0x04:
            return "Pink";
        case 0x05:
            return "Gray";
        case 0x06:
            return "Silver";
        case 0x07:
            return "Gold";
        case 0x08:
            return "Rose Gold";
        case 0x09:
            return "Space Gray";
        case 0x0a:
            return "Dark Blue";
        case 0x0b:
            return "Light Blue";
        case 0x0c:
            return "Yellow";
        default:
            return "Unknown";
        }
    }

    QBluetoothDeviceDiscoveryAgent *discoveryAgent;
    QTimer *refreshTimer;
    QPushButton *scanButton;
    QPushButton *stopButton;
    QTableWidget *deviceTable;
    QGroupBox *detailsGroup;
    QProgressBar *leftBatteryBar;
    QProgressBar *rightBatteryBar;
    QProgressBar *caseBatteryBar;
    QLabel *leftChargingLabel;
    QLabel *rightChargingLabel;
    QLabel *caseChargingLabel;
    QLabel *modelLabel;
    QLabel *statusLabel;
    QLabel *lidOpenLabel;
    QLabel *colorLabel;
    QLabel *rawDataLabel;
    QSystemTrayIcon *trayIcon;

    // Map of discovered devices (address -> device info)
    QMap<QString, DeviceInfo> devices;
};

int main(int argc, char *argv[])
{
    QApplication app(argc, argv);

    BleScanner scanner;
    scanner.show();

    return app.exec();
}

#include "main.moc"