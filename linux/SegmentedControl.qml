pragma ComponentBehavior: Bound

import QtQuick 2.15
import QtQuick.Controls 2.15

Control {
    id: root

    // Properties
    property var model: ["Option 1", "Option 2"] // Default model
    property int currentIndex: 0
    property bool darkMode: false

    // Colors using system palette
    readonly property color backgroundColor: palette.light
    readonly property color selectedColor: palette.highlight
    readonly property color textColor: palette.buttonText
    readonly property color selectedTextColor: palette.highlightedText

    // System palette
    SystemPalette {
        id: palette
    }

    // Internal properties
    padding: 6
    implicitHeight: 32
    implicitWidth: Math.max(200, model.length * 100)

    // Styling
    background: Rectangle {
        radius: height / 2
        color: root.backgroundColor
        border.width: 0
    }

    contentItem: Row {
        spacing: root.padding

        Repeater {
            model: root.model

            delegate: Button {
                required property int index
                required property string modelData

                id: segmentButton
                text: modelData
                width: (root.availableWidth - (root.model.length - 1) * root.padding) / root.model.length
                height: root.availableHeight

                background: Rectangle {
                    radius: height / 2
                    color: root.currentIndex === segmentButton.index ?
                        root.selectedColor : "transparent"
                    border.width: 0

                    Behavior on color {
                        ColorAnimation {
                            duration: 600
                            easing.type: Easing.OutQuad
                        }
                    }
                }

                contentItem: Text {
                    text: segmentButton.text
                    font: segmentButton.font
                    opacity: enabled ? 1.0 : 0.3
                    color: root.currentIndex === segmentButton.index ?
                        root.selectedTextColor : root.textColor
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                    elide: Text.ElideRight

                    Behavior on color {
                        ColorAnimation {
                            duration: 150
                            easing.type: Easing.OutQuad
                        }
                    }
                }

                onClicked: {
                    if (root.currentIndex !== index) {
                        root.currentIndex = index
                    }
                }
            }
        }
    }
}