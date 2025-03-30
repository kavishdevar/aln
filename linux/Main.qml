import QtQuick 2.15
import QtQuick.Controls 2.15
import me.kavishdevar.Battery 1.0

ApplicationWindow {
    visible: true
    width: 400
    height: 300
    title: "AirPods Settings"

    Column {
        anchors.left: parent.left
        anchors.right: parent.right
        spacing: 20
        padding: 20

        // Battery Indicator
        Row {
            // center the content
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: 8

            Column {
                spacing: 5

                Image {
                    source: "qrc:/icons/assets/" + airPodsTrayApp.podIcon
                    width: 72
                    height: 72
                    fillMode: Image.PreserveAspectFit
                    smooth: true
                    antialiasing: true
                    mipmap: true
                    anchors.horizontalCenter: parent.horizontalCenter
                }

                BatteryIndicator {
                    batteryLevel: airPodsTrayApp.battery.leftPodLevel
                    isCharging: airPodsTrayApp.battery.leftPodCharging
                    darkMode: true
                    indicator: "L"
                }
            }

            Column {
                spacing: 5

                Image {
                    source: "qrc:/icons/assets/" + airPodsTrayApp.podIcon
                    mirror: true
                    width: 72
                    height: 72
                    fillMode: Image.PreserveAspectFit
                    smooth: true
                    antialiasing: true
                    mipmap: true
                    anchors.horizontalCenter: parent.horizontalCenter
                }

                BatteryIndicator {
                    batteryLevel: airPodsTrayApp.battery.rightPodLevel
                    isCharging: airPodsTrayApp.battery.rightPodCharging
                    darkMode: true
                    indicator: "R"
                }
            }

            Column {
                spacing: 5
                // hide the case status if battery level is 0 and no pod is in case
                visible: airPodsTrayApp.battery.caseLevel > 0 || airPodsTrayApp.oneOrMorePodsInCase

                Image {
                    source: "qrc:/icons/assets/" + airPodsTrayApp.caseIcon
                    width: 92
                    height: 72
                    fillMode: Image.PreserveAspectFit
                    smooth: true
                    antialiasing: true
                    mipmap: true
                    anchors.horizontalCenter: parent.horizontalCenter
                }

                BatteryIndicator {
                    batteryLevel: airPodsTrayApp.battery.caseLevel
                    isCharging: airPodsTrayApp.battery.caseCharging
                    darkMode: true
                }
            }
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
