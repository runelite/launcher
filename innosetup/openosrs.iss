[Setup]
AppName=OpenOSRS Launcher
AppPublisher=OpenOSRS
UninstallDisplayName=OpenOSRS
AppVersion=@project.version@
AppSupportURL=https://openosrs.com/
DefaultDirName={localappdata}\OpenOSRS
; vcredist queues files to be replaced at next reboot, however it doesn't seem to matter
RestartIfNeededByRun=no

; ~30 mb for the repo the launcher downloads
ExtraDiskSpaceRequired=30000000
ArchitecturesAllowed=x64
PrivilegesRequired=lowest

WizardSmallImageFile=@basedir@/innosetup/openosrs_small.bmp
SetupIconFile=@basedir@/openosrs.ico
UninstallDisplayIcon={app}\OpenOSRS.exe

Compression=lzma2
SolidCompression=yes

OutputDir=@basedir@
OutputBaseFilename=OpenOSRSSetup

[Tasks]
Name: DesktopIcon; Description: "Create a &desktop icon";

[Files]
Source: "@basedir@\native-win64\OpenOSRS.exe"; DestDir: "{app}"
Source: "@basedir@\native-win64\OpenOSRS-shaded.jar"; DestDir: "{app}"
Source: "@basedir@\native-win64\config.json"; DestDir: "{app}"
Source: "@basedir@\native-win64\jre\*"; DestDir: "{app}\jre"; Flags: recursesubdirs
Source: "@basedir@\vcredist_x64.exe"; DestDir: {tmp}; Flags: deleteafterinstall

[Icons]
; start menu
Name: "{userprograms}\OpenOSRS"; Filename: "{app}\OpenOSRS.exe"
Name: "{userdesktop}\OpenOSRS"; Filename: "{app}\OpenOSRS.exe"; Tasks: DesktopIcon

[Run]
Filename: "{tmp}\vcredist_x64.exe"; Check: VCRedistNeedsInstall; Parameters: "/install /quiet /norestart"; StatusMsg: "Installing VC++ 2015 (x64) Redistributables..."
Filename: "{app}\OpenOSRS.exe"; Description: "&Open OpenOSRS"; Flags: postinstall skipifsilent nowait

[InstallDelete]
; Delete the old jvm so it doesn't try to load old stuff with the new vm and crash
Type: filesandordirs; Name: "{app}"

[UninstallDelete]
Type: filesandordirs; Name: "{%USERPROFILE}\.runelite\repository2"

; Code to check if installing the redistributables is necessary - https://stackoverflow.com/a/11172939/7189686
[Code]
type
  INSTALLSTATE = Longint;
const
  INSTALLSTATE_INVALIDARG = -2;  { An invalid parameter was passed to the function. }
  INSTALLSTATE_UNKNOWN = -1;     { The product is neither advertised or installed. }
  INSTALLSTATE_ADVERTISED = 1;   { The product is advertised but not installed. }
  INSTALLSTATE_ABSENT = 2;       { The product is installed for a different user. }
  INSTALLSTATE_DEFAULT = 5;      { The product is installed for the current user. }

  { Visual C++ 2015 Redistributable 14.0.23026 }
  VC_2015_REDIST_X64_MIN = '{0D3E9E15-DE7A-300B-96F1-B4AF12B96488}';
  VC_2015_REDIST_X64_ADD = '{BC958BD2-5DAC-3862-BB1A-C1BE0790438D}';

function MsiQueryProductState(szProduct: string): INSTALLSTATE;
  external 'MsiQueryProductStateA@msi.dll stdcall';

function VCVersionInstalled(const ProductID: string): Boolean;
begin
  Result := MsiQueryProductState(ProductID) = INSTALLSTATE_DEFAULT;
end;

function VCRedistNeedsInstall: Boolean;
begin
  Result := not (VCVersionInstalled(VC_2015_REDIST_X64_MIN) and
    VCVersionInstalled(VC_2015_REDIST_X64_ADD));
end;