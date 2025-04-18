#include "mediacontroller.h"
#include "logger.h"

#include <QDebug>
#include <QProcess>
#include <QRegularExpression>
#include <QDBusConnection>
#include <QDBusConnectionInterface>

MediaController::MediaController(QObject *parent) : QObject(parent) {
  // No additional initialization required here
}

void MediaController::initializeMprisInterface() {
  QStringList services =
      QDBusConnection::sessionBus().interface()->registeredServiceNames();
  QString mprisService;

  for (const QString &service : services) {
    if (service.startsWith("org.mpris.MediaPlayer2.") &&
        service != "org.mpris.MediaPlayer2") {
      mprisService = service;
      break;
    }
  }

  if (!mprisService.isEmpty()) {
    mprisInterface = new QDBusInterface(mprisService, "/org/mpris/MediaPlayer2",
                                        "org.mpris.MediaPlayer2.Player",
                                        QDBusConnection::sessionBus(), this);
    if (!mprisInterface->isValid()) {
      LOG_ERROR("Failed to initialize MPRIS interface for service: ") << mprisService;
    } else {
      LOG_INFO("Connected to MPRIS service: " << mprisService);
    }
  } else {
    LOG_WARN("No active MPRIS media players found");
  }
}

void MediaController::handleEarDetection(const QString &status) {
  bool primaryInEar = false;
  bool secondaryInEar = false;

  QStringList parts = status.split(", ");
  if (parts.size() == 2) {
    primaryInEar = parts[0].contains("In Ear");
    secondaryInEar = parts[1].contains("In Ear");
  }

  LOG_DEBUG("Ear detection status: primaryInEar="
            << primaryInEar << ", secondaryInEar=" << secondaryInEar
            << ", isAirPodsActive=" << isActiveOutputDeviceAirPods());
  if (primaryInEar || secondaryInEar) {
    LOG_INFO("At least one AirPod is in ear");
    activateA2dpProfile();
  } else {
    LOG_INFO("Both AirPods are out of ear");
    removeAudioOutputDevice();
  }

  if (primaryInEar && secondaryInEar) {
    if (wasPausedByApp && isActiveOutputDeviceAirPods()) {
      int result = QProcess::execute("playerctl", QStringList() << "play");
      LOG_DEBUG("Executed 'playerctl play' with result: " << result);
      if (result == 0) {
        LOG_INFO("Resumed playback via Playerctl");
        wasPausedByApp = false;
      } else {
        LOG_ERROR("Failed to resume playback via Playerctl");
      }
    }
  } else {
    if (isActiveOutputDeviceAirPods()) {
      QProcess process;
      process.start("playerctl", QStringList() << "status");
      process.waitForFinished();
      QString playbackStatus = process.readAllStandardOutput().trimmed();
      LOG_DEBUG("Playback status: " << playbackStatus);
      if (playbackStatus == "Playing") {
        pause();
      }
    }
  }
}

void MediaController::followMediaChanges() {
  playerctlProcess = new QProcess(this);
  connect(playerctlProcess, &QProcess::readyReadStandardOutput, this,
          [this]() {
            QString output =
                playerctlProcess->readAllStandardOutput().trimmed();
            LOG_DEBUG("Playerctl output: " << output);
            MediaState state = mediaStateFromPlayerctlOutput(output);
            emit mediaStateChanged(state);
          });
  playerctlProcess->start("playerctl", QStringList() << "--follow" << "status");
}

bool MediaController::isActiveOutputDeviceAirPods() {
  QProcess process;
  process.start("pactl", QStringList() << "get-default-sink");
  process.waitForFinished();
  QString output = process.readAllStandardOutput().trimmed();
  LOG_DEBUG("Default sink: " << output);
  return output.contains(connectedDeviceMacAddress);
}

void MediaController::handleConversationalAwareness(const QByteArray &data) {
  LOG_DEBUG("Handling conversational awareness data: " << data.toHex());
  bool lowered = data[9] == 0x01;
  LOG_INFO("Conversational awareness: " << (lowered ? "enabled" : "disabled"));

  if (lowered) {
    if (initialVolume == -1 && isActiveOutputDeviceAirPods()) {
      QProcess process;
      process.start("pactl", QStringList()
                                 << "get-sink-volume" << "@DEFAULT_SINK@");
      process.waitForFinished();
      QString output = process.readAllStandardOutput();
      QRegularExpression re("front-left: \\d+ /\\s*(\\d+)%");
      QRegularExpressionMatch match = re.match(output);
      if (match.hasMatch()) {
        LOG_DEBUG("Matched: " << match.captured(1));
        initialVolume = match.captured(1).toInt();
      } else {
        LOG_ERROR("Failed to parse initial volume from output: " << output);
        return;
      }
    }
    QProcess::execute(
        "pactl", QStringList() << "set-sink-volume" << "@DEFAULT_SINK@"
                               << QString::number(initialVolume * 0.20) + "%");
    LOG_INFO("Volume lowered to 0.20 of initial which is "
             << initialVolume * 0.20 << "%");
  } else {
    if (initialVolume != -1 && isActiveOutputDeviceAirPods()) {
      QProcess::execute("pactl", QStringList()
                                     << "set-sink-volume" << "@DEFAULT_SINK@"
                                     << QString::number(initialVolume) + "%");
      LOG_INFO("Volume restored to " << initialVolume << "%");
      initialVolume = -1;
    }
  }
}

void MediaController::activateA2dpProfile() {
  if (connectedDeviceMacAddress.isEmpty()) {
    LOG_WARN("Connected device MAC address is empty, cannot activate A2DP profile");
    return;
  }

  LOG_INFO("Activating A2DP profile for AirPods");
  int result = QProcess::execute(
      "pactl", QStringList()
                   << "set-card-profile"
                   << "bluez_card." + connectedDeviceMacAddress << "a2dp-sink");
  if (result != 0) {
    LOG_ERROR("Failed to activate A2DP profile");
  }
}

void MediaController::removeAudioOutputDevice() {
  if (connectedDeviceMacAddress.isEmpty()) {
    LOG_WARN("Connected device MAC address is empty, cannot remove audio output device");
    return;
  }
  
  LOG_INFO("Removing AirPods as audio output device");
  int result = QProcess::execute(
      "pactl", QStringList()
                   << "set-card-profile"
                   << "bluez_card." + connectedDeviceMacAddress << "off");
  if (result != 0) {
    LOG_ERROR("Failed to remove AirPods as audio output device");
  }
}

void MediaController::setConnectedDeviceMacAddress(const QString &macAddress) {
  connectedDeviceMacAddress = macAddress;
}

MediaController::MediaState MediaController::mediaStateFromPlayerctlOutput(
    const QString &output) {
  if (output == "Playing") {
    return MediaState::Playing;
  } else if (output == "Paused") {
    return MediaState::Paused;
  } else {
    return MediaState::Stopped;
  }
}

void MediaController::pause() {
  int result = QProcess::execute("playerctl", QStringList() << "pause");
  LOG_DEBUG("Executed 'playerctl pause' with result: " << result);
  if (result == 0)
  {
    LOG_INFO("Paused playback via Playerctl");
    wasPausedByApp = true;
  }
  else
  {
    LOG_ERROR("Failed to pause playback via Playerctl");
  }
}

MediaController::~MediaController() {
  if (playerctlProcess) {
    playerctlProcess->terminate();
    if (!playerctlProcess->waitForFinished()) {
      playerctlProcess->kill();
      playerctlProcess->waitForFinished(1000);
    }
  }
}