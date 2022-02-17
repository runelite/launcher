call fips set config packr-win32-vs2019-release
call fips clean
call fips build
call copy ..\fips-deploy\packr\packr-win32-vs2019-release\packr.exe ..\..\resources\packr-windows.exe

call fips set config packr-win64-vs2019-release
call fips clean
call fips build
call copy ..\fips-deploy\packr\packr-win64-vs2019-release\packr.exe ..\..\resources\packr-windows-x64.exe
