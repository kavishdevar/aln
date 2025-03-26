cmake_minimum_required(VERSION 3.22.1)

project("l2c_fcr_hook")
set(CMAKE_CXX_STANDARD 23)

add_library(${CMAKE_PROJECT_NAME} SHARED
        l2c_fcr_hook.cpp
        l2c_fcr_hook.h)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log)