[Setup]
AppName=RuneLite
AppPublisher=RuneLite
UninstallDisplayName=RuneLite
AppVersion=Launcher ${project.version}
AppSupportURL=https://runelite.net/
DefaultDirName={localappdata}\RuneLite

; ~30 mb for the repo the launcher downloads
ExtraDiskSpaceRequired=30000000
ArchitecturesAllowed=x86 x64
PrivilegesRequired=lowest

WizardSmallImageFile=${basedir}/innosetup/runelite_small.bmp
SetupIconFile=${basedir}/runelite.ico
UninstallDisplayIcon={app}\RuneLite.exe

Compression=lzma2
SolidCompression=yes

OutputDir=${basedir}
OutputBaseFilename=RuneLiteSetup32

[Tasks]
Name: DesktopIcon; Description: "Create a &desktop icon";

[Files]
Source: "${basedir}\native-win32\RuneLite.exe"; DestDir: "{app}"
Source: "${basedir}\native-win32\RuneLite.jar"; DestDir: "{app}"
Source: "${basedir}\native-win32\config.json"; DestDir: "{app}"
Source: "${basedir}\native-win32\jre\*"; DestDir: "{app}\jre"; Flags: recursesubdirs
; this is a dependency of jvm.dll
Source: "${basedir}\native-win32\jre\bin\msvcr120.dll"; DestDir: "{app}"

[Icons]
; start menu
Name: "{userprograms}\RuneLite"; Filename: "{app}\RuneLite.exe"
Name: "{commondesktop}\RuneLite"; Filename: "{app}\RuneLite.exe"; Tasks: DesktopIcon

[Run]
Filename: "{app}\RuneLite.exe"; Description: "&Open RuneLite"; Flags: postinstall skipifsilent nowait

[UninstallDelete]
Type: filesandordirs; Name: "{%USERPROFILE}\.runelite\repository2"