import QtQuick 2.15
import QtQuick.Controls 2.15

ApplicationWindow {
    visible: true
    width: 400
    height: 300
    title: "AirPods Settings"

    Column {
        spacing: 20
        padding: 20

        Text {
            id: batteryStatus
            text: "Battery Status: " + airPodsTrayApp.batteryStatus
            color: "#ffffff"
        }

        Text {
            id: earDetectionStatus
            text: "Ear Detection Status: " + airPodsTrayApp.earDetectionStatus
            color: "#ffffff"
        }

        ComboBox {
            id: noiseControlMode
            model: ["Off", "Noise Cancellation", "Transparency", "Adaptive"]
            currentIndex: airPodsTrayApp.noiseControlMode
            onCurrentIndexChanged: airPodsTrayApp.noiseControlMode = currentIndex
        }

        Switch {
            id: caToggle
            text: "Conversational Awareness"
            checked: airPodsTrayApp.conversationalAwareness
            onCheckedChanged: airPodsTrayApp.conversationalAwareness = checked
        }

        Slider {
            visible: airPodsTrayApp.adaptiveModeActive
            from: 0
            to: 100
            stepSize: 1
            value: airPodsTrayApp.adaptiveNoiseLevel
            
            property Timer debounceTimer: Timer {
                interval: 500 // 500ms delay after last change
                onTriggered: {
                    if (!parent.pressed) {
                        airPodsTrayApp.setAdaptiveNoiseLevel(parent.value)
                    }
                }
            }
            
            onPressedChanged: {
                if (!pressed) {
                    debounceTimer.stop()
                    airPodsTrayApp.setAdaptiveNoiseLevel(value)
                }
            }
            
            onValueChanged: {
                if (pressed) {
                    debounceTimer.restart()
                }
            }

            Label {
                text: "Adaptive Noise Level: " + parent.value
                color: "#ffffff"
                anchors.top: parent.bottom
            }
        }

        Row {
            spacing: 10

            TextField {
                id: newNameField
                placeholderText: airPodsTrayApp.deviceName
                maximumLength: 32
            }

            Button {
                text: "Rename"
                onClicked: {
                    airPodsTrayApp.renameAirPods(newNameField.text)
                    // Optional: newNameField.text = "" // Clear field after rename
                }
            }
        }
    }
}