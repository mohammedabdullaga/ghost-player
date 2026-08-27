cmake_minimum_required(VERSION 3.22.1)

project(secure_streaming_native)

file(GLOB SECURE_STREAM_SRC byedpi/*.c)
list(REMOVE_ITEM SECURE_STREAM_SRC ${CMAKE_CURRENT_SOURCE_DIR}/byedpi/win_service.c)

add_library(securestreaming SHARED ${SECURE_STREAM_SRC} native-lib.c)
target_include_directories(securestreaming PRIVATE byedpi)

target_compile_options(securestreaming PRIVATE -std=c99 -O2 -Wall -Wno-unused -Wextra -Wno-unused-parameter -pedantic)
target_compile_definitions(securestreaming PRIVATE ANDROID_APP)

target_link_libraries(securestreaming PRIVATE android log)
