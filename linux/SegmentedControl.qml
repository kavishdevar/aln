pragma ComponentBehavior: Bound

import QtQuick 2.15
import QtQuick.Controls 2.15

Control {
    id: root

    // Properties
    property var model: ["Option 1", "Option 2"] // Default model
    property int currentIndex: 0

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
    // Removed: implicitWidth: Math.max(200, model.length * 100)

    // Set focus policy to enable keyboard navigation
    focusPolicy: Qt.StrongFocus
    activeFocusOnTab: true

    // Styling
    background: Rectangle {
        radius: height / 2
        color: root.backgroundColor
        border.width: root.activeFocus ? 1 : 0
        border.color: root.selectedColor
    }

    contentItem: Row {
        spacing: root.padding

        Repeater {
            model: root.model

            delegate: Button {
                id: segmentButton
                required property int index
                required property string modelData
                text: modelData
                // Removed: width: (root.availableWidth - (root.model.length - 1) * root.padding) / root.model.length
                height: root.availableHeight
                focusPolicy: Qt.NoFocus // Let the root control handle focus

                // Add explicit text color
                contentItem: Text {
                    text: segmentButton.text
                    font: segmentButton.font
                    color: root.currentIndex === segmentButton.index ? root.selectedTextColor : root.textColor
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                    leftPadding: 2
                    rightPadding: 2
                    elide: Text.ElideRight
                }

                background: Rectangle {
                    radius: height / 2
                    color: root.currentIndex === segmentButton.index ? root.selectedColor : "transparent"
                    border.width: 0

                    Behavior on color {
                        ColorAnimation {
                            duration: 600
                            easing.type: Easing.OutQuad
                        }
                    }
                }

                onClicked: {
                    if (root.currentIndex !== index) {
                        root.currentIndex = index;
                    }
                }
            }
        }
    }

    // Handle key events for navigation
    Keys.onPressed: event => {
        if (event.key === Qt.Key_Left) {
            if (root.currentIndex > 0) {
                root.currentIndex--;
                event.accepted = true;
            }
        } else if (event.key === Qt.Key_Right) {
            if (root.currentIndex < root.model.length - 1) {
                root.currentIndex++;
                event.accepted = true;
            }
        } else if (event.key === Qt.Key_Home) {
            root.currentIndex = 0;
            event.accepted = true;
        } else if (event.key === Qt.Key_End) {
            root.currentIndex = root.model.length - 1;
            event.accepted = true;
        } else if (event.key >= Qt.Key_1 && event.key <= Qt.Key_9) {
            const index = event.key - Qt.Key_1;
            if (index <= root.model.length) {
                root.currentIndex = index;
                event.accepted = true;
            }
        }
    }
}
