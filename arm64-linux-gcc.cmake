set(CMAKE_SYSTEM_NAME "Linux")
set(CMAKE_C_COMPILER aarch64-linux-gnu-gcc-9)
set(CMAKE_CXX_COMPILER aarch64-linux-gnu-g++-9)
set(CMAKE_C_FLAGS_INIT "-march=armv8-a")
set(CMAKE_CXX_FLAGS_INIT "-march=armv8-a")
set(CMAKE_SYSTEM_PROCESSOR "arm64")
# fips isn't aware it is on Linux when using the cross compiler
set(FIPS_LINUX true)
