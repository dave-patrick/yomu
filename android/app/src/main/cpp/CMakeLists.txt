cmake_minimum_required(VERSION 3.22)
project(yomu_lame_encoder LANGUAGES C CXX)

set(LAME_ROOT "${CMAKE_CURRENT_LIST_DIR}/vendor/lame-3.100")
if(NOT EXISTS "${LAME_ROOT}/config.h")
  message(FATAL_ERROR "The pinned LGPL LAME source is missing from spike/vendor/lame-3.100")
endif()

set(LAME_SOURCES
  VbrTag.c bitstream.c encoder.c fft.c gain_analysis.c id3tag.c lame.c
  newmdct.c presets.c psymodel.c quantize.c quantize_pvt.c reservoir.c
  set_get.c tables.c takehiro.c util.c vbrquantize.c version.c mpglib_interface.c)
list(TRANSFORM LAME_SOURCES PREPEND "${LAME_ROOT}/libmp3lame/")

add_library(mp3lame SHARED ${LAME_SOURCES})
target_include_directories(mp3lame PUBLIC "${LAME_ROOT}/include"
  PRIVATE "${LAME_ROOT}" "${LAME_ROOT}/libmp3lame")
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H=1 LAME_LIBRARY_BUILD=1 STDC_HEADERS=1)
target_compile_options(mp3lame PRIVATE -O2 -fPIC)
target_link_options(mp3lame PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(mp3lame PRIVATE m)

add_library(yomulame SHARED LameJni.cpp)
target_include_directories(yomulame PRIVATE "${LAME_ROOT}/include")
target_compile_options(yomulame PRIVATE -O2 -fvisibility=hidden)
target_link_options(yomulame PRIVATE "-Wl,-z,max-page-size=16384")
find_library(android-log log)
target_link_libraries(yomulame PRIVATE mp3lame ${android-log})
