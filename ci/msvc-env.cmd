@echo off

for /f "usebackq delims=" %%i in (`"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -property installationPath`) do (
    call "%%i\VC\Auxiliary\Build\vcvarsall.bat" %*
    goto :eof
)

echo Failed to find Visual Studio.
exit /b 1