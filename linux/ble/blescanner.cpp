#include "blescanner.h"
#include <QApplication>
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGridLayout>
#include <QLabel>
#include <QPushButton>
#include <QTableWidget>
#include <QHeaderView>
#include <QProgressBar>
#include <QGroupBox>
#include <QMenu>

BleScanner::BleScanner(QWidget *parent) : QMainWindow(parent)
{
    setWindowTitle("AirPods Battery Monitor");
    resize(600, 400);

    QWidget *centralWidget = new QWidget(this);
    QVBoxLayout *mainLayout = new QVBoxLayout(centralWidget);
    setCentralWidget(centralWidget);

    QHBoxLayout *buttonLayout = new QHBoxLayout();
    scanButton = new QPushButton("Start Scan", this);
    stopButton = new QPushButton("Stop Scan", this);
    stopButton->setEnabled(false);
    buttonLayout->addWidget(scanButton);
    buttonLayout->addWidget(stopButton);
    buttonLayout->addStretch();
    mainLayout->addLayout(buttonLayout);

    deviceTable = new QTableWidget(0, 5, this);
    deviceTable->setHorizontalHeaderLabels({"Device", "Left Pod", "Right Pod", "Case", "Address"});
    deviceTable->horizontalHeader()->setSectionResizeMode(QHeaderView::Stretch);
    deviceTable->setSelectionBehavior(QTableWidget::SelectRows);
    deviceTable->setEditTriggers(QTableWidget::NoEditTriggers);
    mainLayout->addWidget(deviceTable);

    detailsGroup = new QGroupBox("Device Details", this);
    QGridLayout *detailsLayout = new QGridLayout(detailsGroup);

    // Row 0: Left Pod
    detailsLayout->addWidget(new QLabel("Left Pod:"), 0, 0);
    leftBatteryBar = new QProgressBar(this);
    leftBatteryBar->setRange(0, 100);
    leftBatteryBar->setTextVisible(true);
    detailsLayout->addWidget(leftBatteryBar, 0, 1);
    leftChargingLabel = new QLabel(this);
    detailsLayout->addWidget(leftChargingLabel, 0, 2);

    // Row 1: Right Pod
    detailsLayout->addWidget(new QLabel("Right Pod:"), 1, 0);
    rightBatteryBar = new QProgressBar(this);
    rightBatteryBar->setRange(0, 100);
    rightBatteryBar->setTextVisible(true);
    detailsLayout->addWidget(rightBatteryBar, 1, 1);
    rightChargingLabel = new QLabel(this);
    detailsLayout->addWidget(rightChargingLabel, 1, 2);

    // Row 2: Case
    detailsLayout->addWidget(new QLabel("Case:"), 2, 0);
    caseBatteryBar = new QProgressBar(this);
    caseBatteryBar->setRange(0, 100);
    caseBatteryBar->setTextVisible(true);
    detailsLayout->addWidget(caseBatteryBar, 2, 1);
    caseChargingLabel = new QLabel(this);
    detailsLayout->addWidget(caseChargingLabel, 2, 2);

    // Row 3: Model
    detailsLayout->addWidget(new QLabel("Model:"), 3, 0);
    modelLabel = new QLabel(this);
    detailsLayout->addWidget(modelLabel, 3, 1);

    // Row 4: Status
    detailsLayout->addWidget(new QLabel("Status:"), 4, 0);
    statusLabel = new QLabel(this);
    detailsLayout->addWidget(statusLabel, 4, 1);

    // Row 5: Lid State (replaces Lid Opens)
    detailsLayout->addWidget(new QLabel("Lid State:"), 5, 0);
    lidStateLabel = new QLabel(this);
    detailsLayout->addWidget(lidStateLabel, 5, 1);

    // Row 6: Color
    detailsLayout->addWidget(new QLabel("Color:"), 6, 0);
    colorLabel = new QLabel(this);
    detailsLayout->addWidget(colorLabel, 6, 1);

    // Row 7: Raw Data
    detailsLayout->addWidget(new QLabel("Raw Data:"), 7, 0);
    rawDataLabel = new QLabel(this);
    rawDataLabel->setWordWrap(true);
    detailsLayout->addWidget(rawDataLabel, 7, 1, 1, 2);

    // New Rows for Additional Info
    // Row 8: Left Pod In Ear
    detailsLayout->addWidget(new QLabel("Left Pod In Ear:"), 8, 0);
    leftInEarLabel = new QLabel(this);
    detailsLayout->addWidget(leftInEarLabel, 8, 1);

    // Row 9: Right Pod In Ear
    detailsLayout->addWidget(new QLabel("Right Pod In Ear:"), 9, 0);
    rightInEarLabel = new QLabel(this);
    detailsLayout->addWidget(rightInEarLabel, 9, 1);

    // Row 10: Left Pod Microphone
    detailsLayout->addWidget(new QLabel("Left Pod Microphone:"), 10, 0);
    leftMicLabel = new QLabel(this);
    detailsLayout->addWidget(leftMicLabel, 10, 1);

    // Row 11: Right Pod Microphone
    detailsLayout->addWidget(new QLabel("Right Pod Microphone:"), 11, 0);
    rightMicLabel = new QLabel(this);
    detailsLayout->addWidget(rightMicLabel, 11, 1);

    // Row 12: This Pod In Case
    detailsLayout->addWidget(new QLabel("This Pod In Case:"), 12, 0);
    thisPodInCaseLabel = new QLabel(this);
    detailsLayout->addWidget(thisPodInCaseLabel, 12, 1);

    // Row 13: One Pod In Case
    detailsLayout->addWidget(new QLabel("One Pod In Case:"), 13, 0);
    onePodInCaseLabel = new QLabel(this);
    detailsLayout->addWidget(onePodInCaseLabel, 13, 1);

    // Row 14: Both Pods In Case
    detailsLayout->addWidget(new QLabel("Both Pods In Case:"), 14, 0);
    bothPodsInCaseLabel = new QLabel(this);
    detailsLayout->addWidget(bothPodsInCaseLabel, 14, 1);

    mainLayout->addWidget(detailsGroup);
    detailsGroup->setVisible(false);

    bleManager = new BleManager(this);
    refreshTimer = new QTimer(this);

    connect(scanButton, &QPushButton::clicked, this, &BleScanner::startScan);
    connect(stopButton, &QPushButton::clicked, this, &BleScanner::stopScan);
    connect(deviceTable, &QTableWidget::itemSelectionChanged, this, &BleScanner::onDeviceSelected);
    connect(refreshTimer, &QTimer::timeout, this, &BleScanner::updateDeviceList);
}

void BleScanner::startScan()
{
    scanButton->setEnabled(false);
    stopButton->setEnabled(true);
    deviceTable->setRowCount(0);
    detailsGroup->setVisible(false);
    bleManager->startScan();
    refreshTimer->start(500);
}

void BleScanner::stopScan()
{
    bleManager->stopScan();
    refreshTimer->stop();
    scanButton->setEnabled(true);
    stopButton->setEnabled(false);
}

void BleScanner::updateDeviceList()
{
    const QMap<QString, DeviceInfo> &devices = bleManager->getDevices();
    QString selectedAddress;
    if (deviceTable->selectionModel()->hasSelection())
    {
        int row = deviceTable->selectionModel()->selectedRows().first().row();
        selectedAddress = deviceTable->item(row, 4)->text();
    }

    deviceTable->setRowCount(0);
    deviceTable->setRowCount(devices.size());
    int row = 0;
    for (auto it = devices.begin(); it != devices.end(); ++it, ++row)
    {
        const DeviceInfo &device = it.value();
        deviceTable->setItem(row, 0, new QTableWidgetItem(device.name));
        QString leftStatus = (device.leftPodBattery >= 0 ? QString::number(device.leftPodBattery) + "%" : "N/A") +
                             (device.leftCharging ? " ⚡" : "");
        deviceTable->setItem(row, 1, new QTableWidgetItem(leftStatus));
        QString rightStatus = (device.rightPodBattery >= 0 ? QString::number(device.rightPodBattery) + "%" : "N/A") +
                              (device.rightCharging ? " ⚡" : "");
        deviceTable->setItem(row, 2, new QTableWidgetItem(rightStatus));
        QString caseStatus = (device.caseBattery >= 0 ? QString::number(device.caseBattery) + "%" : "N/A") +
                             (device.caseCharging ? " ⚡" : "");
        deviceTable->setItem(row, 3, new QTableWidgetItem(caseStatus));
        deviceTable->setItem(row, 4, new QTableWidgetItem(device.address));
        if (device.address == selectedAddress)
        {
            deviceTable->selectRow(row);
        }
    }

    if (deviceTable->selectedItems().isEmpty()) {
        deviceTable->selectRow(0);
    }
}

void BleScanner::onDeviceSelected()
{
    if (!deviceTable->selectionModel()->hasSelection())
    {
        detailsGroup->setVisible(false);
        return;
    }

    int row = deviceTable->selectionModel()->selectedRows().first().row();
    QString address = deviceTable->item(row, 4)->text();
    const QMap<QString, DeviceInfo> &devices = bleManager->getDevices();
    if (!devices.contains(address))
    {
        detailsGroup->setVisible(false);
        return;
    }

    const DeviceInfo &device = devices[address];

    // Battery bars with N/A handling
    if (device.leftPodBattery >= 0)
    {
        leftBatteryBar->setValue(device.leftPodBattery);
        leftBatteryBar->setFormat("%p%");
    }
    else
    {
        leftBatteryBar->setValue(0);
        leftBatteryBar->setFormat("N/A");
    }

    if (device.rightPodBattery >= 0)
    {
        rightBatteryBar->setValue(device.rightPodBattery);
        rightBatteryBar->setFormat("%p%");
    }
    else
    {
        rightBatteryBar->setValue(0);
        rightBatteryBar->setFormat("N/A");
    }

    if (device.caseBattery >= 0)
    {
        caseBatteryBar->setValue(device.caseBattery);
        caseBatteryBar->setFormat("%p%");
    }
    else
    {
        caseBatteryBar->setValue(0);
        caseBatteryBar->setFormat("N/A");
    }

    leftChargingLabel->setText(device.leftCharging ? "Charging" : "Not Charging");
    rightChargingLabel->setText(device.rightCharging ? "Charging" : "Not Charging");
    caseChargingLabel->setText(device.caseCharging ? "Charging" : "Not Charging");

    QString modelName = getModelName(device.deviceModel);
    modelLabel->setText(modelName + " (0x" + QString::number(device.deviceModel, 16).toUpper() + ")");

    QString statusBinary = QString("%1").arg(device.status, 8, 2, QChar('0'));
    statusLabel->setText(QString("0x%1 (%2) - Binary: %3")
                             .arg(device.status, 2, 16, QChar('0'))
                             .toUpper()
                             .arg(device.status)
                             .arg(statusBinary));

    // Lid State enum handling
    QString lidStateStr;

    switch (device.lidState)
    {
    case DeviceInfo::LidState::OPEN:
        lidStateStr.append("Open");
        break;
    case DeviceInfo::LidState::CLOSED:
        lidStateStr.append("Closed");
        break;
    case DeviceInfo::LidState::NOT_IN_CASE:
        lidStateStr.append("Not in Case");
        break;
    case DeviceInfo::LidState::UNKNOWN:
        lidStateStr.append("Unknown");
        break;
    }
    lidStateStr.append(" (0x" + QString::number(device.lidOpenCounter, 16).toUpper() + " = " + QString::number(device.lidOpenCounter) + ")");
    lidStateLabel->setText(lidStateStr);

    QString colorName = getColorName(device.deviceColor);
    colorLabel->setText(colorName + " (" + QString::number(device.deviceColor) + ")");

    QString rawDataStr = "Bytes: ";
    for (int i = 0; i < device.rawData.size(); ++i)
    {
        rawDataStr += QString("0x%1 ").arg(static_cast<quint8>(device.rawData[i]), 2, 16, QChar('0')).toUpper();
    }
    rawDataLabel->setText(rawDataStr);

    // Set new status labels
    leftInEarLabel->setText(device.isLeftPodInEar ? "Yes" : "No");
    rightInEarLabel->setText(device.isRightPodInEar ? "Yes" : "No");
    leftMicLabel->setText(device.isLeftPodMicrophone ? "Yes" : "No");
    rightMicLabel->setText(device.isRightPodMicrophone ? "Yes" : "No");
    thisPodInCaseLabel->setText(device.isThisPodInTheCase ? "Yes" : "No");
    onePodInCaseLabel->setText(device.isOnePodInCase ? "Yes" : "No");
    bothPodsInCaseLabel->setText(device.areBothPodsInCase ? "Yes" : "No");

    detailsGroup->setVisible(true);
}

QString BleScanner::getModelName(quint16 modelId)
{
    switch (modelId)
    {
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
    case 0x0A20:
        return "AirPods Max";
    case 0x1F20:
        return "AirPods Max (USB-C)";
    case 0x0E20:
        return "AirPods Pro";
    case 0x1420:
        return "AirPods Pro 2nd Gen";
    case 0x2420:
        return "AirPods Pro 2nd Gen (USB-C)";
    default:
        return "Unknown Apple Device";
    }
}

QString BleScanner::getColorName(quint8 colorId)
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
    case 0x0A:
        return "Dark Blue";
    case 0x0B:
        return "Light Blue";
    case 0x0C:
        return "Yellow";
    default:
        return "Unknown";
    }
}