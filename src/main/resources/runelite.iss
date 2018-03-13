[Setup]
AppName=RuneLite
AppPublisher=RuneLite
UninstallDisplayName=RuneLite
AppVersion=Launcher ${project.version}
AppSupportURL=https://runelite.net/
DefaultDirName={localappdata}\RuneLite

; ~30 mb for the repo the launcher downloads
ExtraDiskSpaceRequired=30000000
ArchitecturesAllowed=x64
PrivilegesRequired=lowest

WizardSmallImageFile=runelite_small.bmp
SetupIconFile=${basedir}/src/main/resources/runelite.ico
UninstallDisplayIcon={app}\RuneLite.exe

Compression=lzma2
SolidCompression=yes

OutputDir=${basedir}
OutputBaseFilename=RuneLiteSetup

[Tasks]
Name: DesktopIcon; Description: "Create a &desktop icon";

[Files]
Source: "${project.build.directory}\native\win64\RuneLite.exe"; DestDir: "{app}"
Source: "${project.build.directory}\native\win64\RuneLite.jar"; DestDir: "{app}"
Source: "${project.build.directory}\native\win64\config.json"; DestDir: "{app}"
Source: "${project.build.directory}\native\win64\jre\*"; DestDir: "{app}\jre"; Flags: recursesubdirs
Source: "${project.build.directory}\native\win64\jre\bin\msvcr100.dll"; DestDir: "{app}"

[Icons]
; start menu
Name: "{userprograms}\RuneLite"; Filename: "{app}\RuneLite.exe"
Name: "{commondesktop}\RuneLite"; Filename: "{app}\RuneLite.exe"; Tasks: DesktopIcon

[Run]
Filename: "{app}\RuneLite.exe"; Description: "&Open RuneLite"; Flags: postinstall skipifsilent nowait

[UninstallDelete]
Type: filesandordirs; Name: "{%USERPROFILE}\.runelite\repository"