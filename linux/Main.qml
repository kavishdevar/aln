import QtQuick 2.15
import QtQuick.Controls 2.15

ApplicationWindow {
    id: mainWindow
    visible: true
    width: 400
    height: 300
    title: "AirPods Settings"

    onClosing: mainWindow.visible = false

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
                placeholderText: airPodsTrayApp.deviceName
                maximumLength: 32
            }

            Button {
                text: "Rename"
                onClicked: airPodsTrayApp.renameAirPods(newNameField.text)
            }
        }
    }
}