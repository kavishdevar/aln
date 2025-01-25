import QtQuick 2.15
import QtQuick.Controls 2.15

ApplicationWindow {
    visible: true
    width: 400
    height: 300
    title: "AirPods Settings"
    property bool ignoreNoiseControlChange: false
    property bool isPlaying: false

    Component.onCompleted: {
        caToggle.checked = airPodsTrayApp.loadConversationalAwarenessState()
    }

    Column {
        spacing: 20
        padding: 20

        Text {
            text: "Battery Status: "
            id: batteryStatus
            objectName: "batteryStatus"
        }

        Text {
            text: "Ear Detection Status: "
            id: earDetectionStatus
            objectName: "earDetectionStatus"
        }

        ComboBox {
            id: noiseControlMode
            model: ["Off", "Noise Cancellation", "Transparency", "Adaptive"]
            currentIndex: 0
            onCurrentIndexChanged: {
                if (!ignoreNoiseControlChange) {
                    airPodsTrayApp.setNoiseControlMode(currentIndex)
                }
            }
            Connections {
                target: airPodsTrayApp
                onNoiseControlModeChanged: {
                    ignoreNoiseControlChange = true
                    noiseControlMode.currentIndex = mode;
                    ignoreNoiseControlChange = false
                }
            }
        }

        Switch {
            id: caToggle
            text: "Conversational Awareness"
            checked: isPlaying
            onCheckedChanged: {
                airPodsTrayApp.setConversationalAwareness(checked)
                airPodsTrayApp.saveConversationalAwarenessState(checked)
            }
        }
    }
}
