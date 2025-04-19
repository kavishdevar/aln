// Icon.qml
import QtQuick 2.15

Text {
    property string icon: ""
    font.family: iconFont.name
    text: icon

    FontLoader {
        id: iconFont
        source: "qrc:/icons/assets/fonts/SF-Symbols-6.ttf"
    }
}