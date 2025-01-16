import QtQuick 2.15
import QtQuick.Controls 2.15

ApplicationWindow {
    visible: true
    width: 400
    height: 300
    title: "AirPods Settings"
    property bool ignoreNoiseControlChange: false

    Column {
        spacing: 20
        padding: 20

        Text {
            text: "Ear Detection Status: "
            id: earDetectionStatus
        }

        Text {
            text: "Battery Status: "
            id: batteryStatus
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
                function onNoiseControlModeChanged(mode) {
                    ignoreNoiseControlChange = true
                    noiseControlMode.currentIndex = mode;
                    ignoreNoiseControlChange = false
                }
            }
        }

        Switch {
            id: caToggle
            text: "Conversational Awareness"
            onCheckedChanged: {
                airPodsTrayApp.setConversationalAwareness(checked)
            }
        }
    }
}
