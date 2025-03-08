#ifndef MEDIACONTROLLER_H
#define MEDIACONTROLLER_H

#include <QDBusInterface>
#include <QObject>

class QProcess;

class MediaController : public QObject {
  Q_OBJECT
public:
  enum MediaState { Playing, Paused, Stopped };

  explicit MediaController(QObject *parent = nullptr);
  ~MediaController();
  
  void initializeMprisInterface();
  void handleEarDetection(const QString &status);
  void followMediaChanges();
  bool isActiveOutputDeviceAirPods();
  void handleConversationalAwareness(const QByteArray &data);
  void activateA2dpProfile();
  void removeAudioOutputDevice();
  void setConnectedDeviceMacAddress(const QString &macAddress);

Q_SIGNALS:
  void mediaStateChanged(MediaState state);

private:
  MediaState mediaStateFromPlayerctlOutput(const QString &output);

  QDBusInterface *mprisInterface = nullptr;
  QProcess *playerctlProcess = nullptr;
  bool wasPausedByApp = false;
  int initialVolume = -1;
  QString connectedDeviceMacAddress;
};

#endif // MEDIACONTROLLER_H
