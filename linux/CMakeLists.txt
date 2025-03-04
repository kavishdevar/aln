cmake_minimum_required(VERSION 3.16)

project(linux VERSION 0.1 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(Qt6 6.5 REQUIRED COMPONENTS Quick Widgets Bluetooth Multimedia DBus)

qt_standard_project_setup(REQUIRES 6.5)

qt_add_executable(applinux
    main.cpp
    main.h
    airpods_packets.h
)

qt_add_qml_module(applinux
    URI linux
    VERSION 1.0
    QML_FILES
        Main.qml
)

target_link_libraries(applinux
    PRIVATE Qt6::Quick Qt6::Widgets Qt6::Bluetooth Qt6::Multimedia Qt6::DBus
)

include(GNUInstallDirs)
install(TARGETS applinux
    BUNDLE DESTINATION .
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
)
