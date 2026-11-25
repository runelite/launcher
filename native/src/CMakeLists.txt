if(MSVC)
    add_compile_options(/W4)
else()
    add_compile_options(-Wall)
endif()

add_executable(packr WIN32 packr.cpp)
set_target_properties(packr PROPERTIES OUTPUT_NAME "RuneLite")
if(WIN32)
    target_link_libraries(packr bcrypt wintrust)
    set_target_properties(packr PROPERTIES LINK_FLAGS "/LARGEADDRESSAWARE")
    target_sources(packr PUBLIC win32/packr_win32.cpp win32/runelite.manifest win32/runelite.rc)
    set_property(TARGET packr PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded") # multi-threaded statically-linked runtime
elseif(APPLE)
    target_sources(packr PUBLIC macos/packr_macos.cpp)

    find_library(CORE_FOUNDATION CoreFoundation)
    find_library(CORE_GRAPHICS CoreGraphics)
    target_link_libraries(packr ${CORE_FOUNDATION} ${CORE_GRAPHICS})
else()
    target_sources(packr PUBLIC linux/packr_linux.cpp)
    target_link_libraries(packr dl pthread Threads::Threads)
endif()
target_link_libraries(packr dropt)

if(WIN32 OR APPLE)
    set(RUNELITE_JAR "${PROJECT_SOURCE_DIR}/../build/libs/RuneLite.jar")
    set(JAR_HASH_HEADER "${CMAKE_CURRENT_BINARY_DIR}/jar_hash.h")
    add_custom_command(OUTPUT "${JAR_HASH_HEADER}"
        COMMAND "${CMAKE_COMMAND}" "-DJAR=${RUNELITE_JAR}" "-DHEADER=${JAR_HASH_HEADER}"
            -P "${PROJECT_SOURCE_DIR}/cmake/jar_hash.cmake"
        DEPENDS "${RUNELITE_JAR}" "${PROJECT_SOURCE_DIR}/cmake/jar_hash.cmake"
        VERBATIM)
    target_sources(packr PRIVATE sha256.cpp "${JAR_HASH_HEADER}")
    target_include_directories(packr PRIVATE "${CMAKE_CURRENT_BINARY_DIR}")
endif()
