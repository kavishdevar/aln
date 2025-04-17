import QtQuick 2.15

Column {
    property bool isVisible: true
    property bool inEar: true
    property string iconSource
    property int batteryLevel: 0
    property bool isCharging: false
    property string indicator: ""

    spacing: 5
    opacity: inEar ? 1 : 0.5
    visible: isVisible

    Image {
        source: parent.iconSource
        width: parent.indicator === "" ? 92 : 72
        height: 72
        fillMode: Image.PreserveAspectFit
        mipmap: true
        mirror: parent.indicator === "R"
        anchors.horizontalCenter: parent.horizontalCenter
    }

    BatteryIndicator {
        batteryLevel: parent.batteryLevel
        isCharging: parent.isCharging
        indicator: parent.indicator
    }
}