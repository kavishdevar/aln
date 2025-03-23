#ifndef BLESCANNER_H
#define BLESCANNER_H

#include <QMainWindow>
#include "blemanager.h"
#include <QTimer>
#include <QSystemTrayIcon>

class QTableWidget;
class QGroupBox;
class QProgressBar;
class QLabel;
class QPushButton;

class BleScanner : public QMainWindow
{
    Q_OBJECT
public:
    explicit BleScanner(QWidget *parent = nullptr);

private slots:
    void startScan();
    void stopScan();
    void updateDeviceList();
    void onDeviceSelected();

private:
    QString getModelName(quint16 modelId);
    QString getColorName(quint8 colorId);
    QString getConnectionStateName(DeviceInfo::ConnectionState state);

    BleManager *bleManager;
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
    QLabel *lidStateLabel; // Renamed from lidOpenLabel
    QLabel *colorLabel;
    QLabel *rawDataLabel;

    // New labels for additional DeviceInfo fields
    QLabel *leftInEarLabel;
    QLabel *rightInEarLabel;
    QLabel *leftMicLabel;
    QLabel *rightMicLabel;
    QLabel *thisPodInCaseLabel;
    QLabel *onePodInCaseLabel;
    QLabel *bothPodsInCaseLabel;
    QLabel *connectionStateLabel;
};

#endif // BLESCANNER_H