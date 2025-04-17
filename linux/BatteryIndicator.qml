import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Layouts 1.15

// BatteryIndicator.qml
Rectangle {
    id: root

    // Public properties
    property int batteryLevel: 50 // 0-100
    property bool isCharging: false
    property string indicator: "" // "L" or "R"
    
    readonly property bool darkMode: {
        return palette.window.hslLightness < palette.windowText.hslLightness;
    }

    readonly property color batteryLowColor: "#FF453A"
    readonly property color batteryMediumColor: "#FFD60A"
    readonly property color batteryHighColor: "#30D158"
    readonly property color chargingColor: "#30D158"
    readonly property color backgroundColor: palette.buttonText
    readonly property color indicatorTextColor: palette.window
    readonly property color textColor: palette.text
    readonly property color borderColor: darkMode ? Qt.rgba(1, 1, 1, 0.3) : Qt.rgba(0, 0, 0, 0.3)

    // Size parameters
    width: 85
    height: 40
    color: "transparent"

    // System palette
    SystemPalette {
        id: palette
    }

    // Battery level color based on percentage
    readonly property color levelColor: {
        if (isCharging) return chargingColor;
        if (batteryLevel <= 20) return batteryLowColor;
        if (batteryLevel <= 50) return batteryMediumColor;
        return batteryHighColor;
    }

    ColumnLayout {
        anchors.fill: parent
        spacing: 7

        // Battery icon
        Item {
            id: batteryIcon
            Layout.preferredWidth: 32
            Layout.preferredHeight: 16
            Layout.alignment: Qt.AlignHCenter

            // Main battery body
            Rectangle {
                id: batteryBody
                width: parent.width - 2
                height: parent.height
                radius: 3
                color: "transparent"
                border.width: 1.5
                border.color: root.borderColor

                // Battery level fill
                Rectangle {
                    id: batteryFill
                    width: Math.max(2, (batteryBody.width - 4) * (root.batteryLevel / 100))
                    height: batteryBody.height - 4
                    anchors.left: parent.left
                    anchors.leftMargin: 2
                    anchors.verticalCenter: parent.verticalCenter
                    radius: 1.5
                    color: root.levelColor

                    // Animation for smooth transitions
                    Behavior on width {
                        NumberAnimation { duration: 300; easing.type: Easing.OutCubic }
                    }

                    // Flash effect when charging
                    SequentialAnimation {
                        running: root.isCharging
                        loops: Animation.Infinite
                        alwaysRunToEnd: true
                        NumberAnimation { target: batteryFill; property: "opacity"; to: 0.7; duration: 3000 }
                        NumberAnimation { target: batteryFill; property: "opacity"; to: 1.0; duration: 3000 }
                    }
                }
            }

            // Battery positive terminal
            Rectangle {
                width: 2
                height: 8
                radius: 1
                color: root.borderColor
                anchors.left: batteryBody.right
                anchors.verticalCenter: batteryBody.verticalCenter
            }

            // Alternative charging bolt using Canvas
            Canvas {
                id: chargingBolt
                visible: root.isCharging
                width: 14
                height: 14
                anchors.centerIn: batteryBody

                onPaint: {
                    var ctx = getContext("2d");
                    ctx.reset();

                    // Draw a lightning bolt
                    ctx.fillStyle = root.darkMode ? "#FFFFFF" : "#000000";
                    ctx.beginPath();
                    ctx.moveTo(7, 2); // Top point
                    ctx.lineTo(3, 8); // Middle left
                    ctx.lineTo(6, 8); // Middle center
                    ctx.lineTo(5, 12); // Bottom point
                    ctx.lineTo(11, 6); // Middle right
                    ctx.lineTo(8, 6); // Middle center
                    ctx.lineTo(9, 2); // Back to top
                    ctx.closePath();
                    ctx.fill();
                }
            }
        }

        // Text container
        RowLayout {
            Layout.alignment: Qt.AlignHCenter
            spacing: 4

            // Left/Right indicator
            Rectangle {
                id: indicatorBackground
                visible: root.indicator !== ""
                Layout.preferredWidth: 16
                Layout.preferredHeight: 16
                radius: width / 2
                color: root.backgroundColor

                Text {
                    id: indicatorText
                    anchors.centerIn: parent
                    text: root.indicator
                    color: root.indicatorTextColor
                    font.pixelSize: 10
                    font.family: "SF Pro Text"
                }
            }

            // Battery percentage
            Text {
                id: percentageText
                text: root.batteryLevel + "%"
                color: root.textColor
                font.pixelSize: 12
                font.family: "SF Pro Text"
            }
        }
    }
}
