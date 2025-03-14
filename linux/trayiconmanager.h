#include <QObject>
#include <QSystemTrayIcon>

#include "enums.h"

class QMenu;
class QAction;
class QActionGroup;

class TrayIconManager : public QObject
{
    Q_OBJECT

public:
    explicit TrayIconManager(QObject *parent = nullptr);

    void updateBatteryStatus(const QString &status);

    void updateNoiseControlState(AirpodsTrayApp::Enums::NoiseControlMode);

    void updateConversationalAwareness(bool enabled);

private slots:
    void onTrayIconActivated(QSystemTrayIcon::ActivationReason reason);

private:
    QSystemTrayIcon *trayIcon;
    QMenu *trayMenu;
    QAction *caToggleAction;
    QActionGroup *noiseControlGroup;

    void setupMenuActions();

    void updateIconFromBattery(const QString &status);

signals:
    void trayClicked();
    void noiseControlChanged(AirpodsTrayApp::Enums::NoiseControlMode);
    void conversationalAwarenessToggled(bool enabled);
};