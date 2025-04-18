pragma ComponentBehavior: Bound

import QtQuick 2.15
import QtQuick.Controls 2.15

ApplicationWindow {
    id: mainWindow
    visible: true
    width: 400
    height: 300
    title: "AirPods Settings"

    onClosing: mainWindow.visible = false

    // Mouse area for handling back/forward navigation
    MouseArea {
        anchors.fill: parent
        acceptedButtons: Qt.BackButton | Qt.ForwardButton
        onClicked: (mouse) => {
            if (mouse.button === Qt.BackButton && stackView.depth > 1) {
                stackView.pop()
            } else if (mouse.button === Qt.ForwardButton) {
                console.log("Forward button pressed")
            }
        }
    }

    StackView {
        id: stackView
        anchors.fill: parent
        initialItem: mainPage
    }

    FontLoader {
        id: iconFont
        source: "qrc:/icons/assets/fonts/SF-Symbols-6.ttf"
    }

    Component {
        id: mainPage
        Item {
            Column {
                anchors.fill: parent
                spacing: 20
                padding: 20

                // Connection status indicator (Apple-like pill shape)
                Rectangle {
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.topMargin: 10
                    width: 120
                    height: 24
                    radius: 12
                    color: airPodsTrayApp.airpodsConnected ? "#30D158" : "#FF453A"
                    opacity: 0.8
                    visible: !airPodsTrayApp.airpodsConnected

                    Label {
                        anchors.centerIn: parent
                        text: airPodsTrayApp.airpodsConnected ? "Connected" : "Disconnected"
                        color: "white"
                        font.pixelSize: 12
                        font.weight: Font.Medium
                    }
                }

                // Battery Indicator Row
                Row {
                    anchors.horizontalCenter: parent.horizontalCenter
                    spacing: 8

                    PodColumn {
                        isVisible: airPodsTrayApp.battery.leftPodAvailable
                        inEar: airPodsTrayApp.leftPodInEar
                        iconSource: "qrc:/icons/assets/" + airPodsTrayApp.podIcon
                        batteryLevel: airPodsTrayApp.battery.leftPodLevel
                        isCharging: airPodsTrayApp.battery.leftPodCharging
                        indicator: "L"
                    }

                    PodColumn {
                        isVisible: airPodsTrayApp.battery.rightPodAvailable
                        inEar: airPodsTrayApp.rightPodInEar
                        iconSource: "qrc:/icons/assets/" + airPodsTrayApp.podIcon
                        batteryLevel: airPodsTrayApp.battery.rightPodLevel
                        isCharging: airPodsTrayApp.battery.rightPodCharging
                        indicator: "R"
                    }

                    PodColumn {
                        isVisible: airPodsTrayApp.battery.caseAvailable
                        inEar: true
                        iconSource: "qrc:/icons/assets/" + airPodsTrayApp.caseIcon
                        batteryLevel: airPodsTrayApp.battery.caseLevel
                        isCharging: airPodsTrayApp.battery.caseCharging
                    }
                }

                SegmentedControl {
                    anchors.horizontalCenter: parent.horizontalCenter
                    model: ["Off", "Noise Cancellation", "Transparency", "Adaptive"]
                    currentIndex: airPodsTrayApp.noiseControlMode
                    onCurrentIndexChanged: airPodsTrayApp.noiseControlMode = currentIndex
                    visible: airPodsTrayApp.airpodsConnected
                }

                Slider {
                    visible: airPodsTrayApp.adaptiveModeActive
                    from: 0
                    to: 100
                    stepSize: 1
                    value: airPodsTrayApp.adaptiveNoiseLevel

                    Timer {
                        id: debounceTimer
                        interval: 500
                        onTriggered: if (!parent.pressed) airPodsTrayApp.setAdaptiveNoiseLevel(parent.value)
                    }

                    onPressedChanged: if (!pressed) airPodsTrayApp.setAdaptiveNoiseLevel(value)
                    onValueChanged: if (pressed) debounceTimer.restart()

                    Label {
                        text: "Adaptive Noise Level: " + parent.value
                        anchors.top: parent.bottom
                    }
                }

                Switch {
                    text: "Conversational Awareness"
                    checked: airPodsTrayApp.conversationalAwareness
                    onCheckedChanged: airPodsTrayApp.conversationalAwareness = checked
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
                        onClicked: airPodsTrayApp.renameAirPods(newNameField.text)
                    }
                }
            }

            RoundButton {
                anchors.top: parent.top
                anchors.right: parent.right
                anchors.margins: 10
                font.family: iconFont.name
                font.pixelSize: 18
                text: "\uf958" // U+F958
                onClicked: stackView.push(settingsPage)
            }
        }
    }

    Component {
        id: settingsPage
        Item {
            ScrollView {
                anchors.fill: parent
                contentWidth: parent.width
                contentHeight: parent.height

                Column {
                    spacing: 20
                    padding: 20

                    Label {
                        text: "Settings"
                        font.pixelSize: 24
                        // center the label
                        anchors.horizontalCenter: parent.horizontalCenter
                    }

                    ComboBox {
                        anchors.horizontalCenter: parent.horizontalCenter
                        model: ["Pause When One Removed", "Pause When Both Removed", "Never Pause"]
                        currentIndex: airPodsTrayApp.earDetectionBehavior
                        onActivated: {
                            airPodsTrayApp.earDetectionBehavior = currentIndex
                        }
                    }
                }
            }

            // Floating back button
            RoundButton {
                anchors.top: parent.top
                anchors.left: parent.left
                anchors.margins: 10
                font.family: iconFont.name
                font.pixelSize: 18
                text: "\uecb1" // U+ECB1
                onClicked: stackView.pop()
            }
        }
    }
}