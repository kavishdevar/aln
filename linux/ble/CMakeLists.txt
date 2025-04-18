cmake_minimum_required(VERSION 3.16)

project(ble_monitor VERSION 0.1 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)

find_package(Qt6 6.5 REQUIRED COMPONENTS Core Bluetooth Widgets)

qt_add_executable(ble_monitor
    main.cpp
    blemanager.h
    blemanager.cpp
    blescanner.h
    blescanner.cpp
    proximityparser.h
    proximityparser.cpp
)

target_link_libraries(ble_monitor
    PRIVATE Qt6::Core Qt6::Bluetooth Qt6::Widgets
)

install(TARGETS ble_monitor
    BUNDLE DESTINATION .
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
)