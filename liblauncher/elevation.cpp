#include <Windows.h>
#include <shlobj.h>
#include <atlbase.h>
#include <jni.h>

extern "C" JNIEXPORT jboolean JNICALL Java_net_runelite_launcher_FilePermissionManager_isRunningElevated(JNIEnv *env, jclass clazz, jlong pid) {
	BOOL fRet = false;
	HANDLE hToken = nullptr;
	HANDLE handle = OpenProcess(PROCESS_ALL_ACCESS, TRUE, pid);
	// alternatively use GetCurrentProcess() instead if you'd only want to check the current process
	if (OpenProcessToken(handle, TOKEN_QUERY, &hToken)) {
		TOKEN_ELEVATION Elevation;
		DWORD cbSize = sizeof(TOKEN_ELEVATION);
		if (GetTokenInformation(hToken, TokenElevation, &Elevation, sizeof(Elevation), &cbSize)) {
			fRet = Elevation.TokenIsElevated;
		}
	}
	CloseHandle(handle);
	if (hToken) {
		CloseHandle(hToken);
	}
	bool result = fRet == 1 ? true : false;
	return result;
}

extern "C" JNIEXPORT void JNICALL Java_net_runelite_launcher_FilePermissionManager_elevate(JNIEnv *env, jclass clazz, jstring pathObj, jstring argsObj) {
	const jchar *pathString = env->GetStringChars(pathObj, nullptr);
	const jchar *argsString = env->GetStringChars(argsObj, nullptr);

	ShellExecuteW(nullptr, L"runas", reinterpret_cast<const wchar_t *>(pathString), reinterpret_cast<const wchar_t *>(argsString), nullptr, SW_SHOWNORMAL);

	env->ReleaseStringChars(pathObj, pathString);
	env->ReleaseStringChars(argsObj, argsString);
}

// https://devblogs.microsoft.com/oldnewthing/20130318-00/?p=4933
void FindDesktopFolderView(REFIID riid, void** ppv) {
	CComPtr<IShellWindows> spShellWindows;
	spShellWindows.CoCreateInstance(CLSID_ShellWindows);

	CComVariant vtLoc(CSIDL_DESKTOP);
	CComVariant vtEmpty;
	long lhwnd;
	CComPtr<IDispatch> spdisp;
	spShellWindows->FindWindowSW(
		&vtLoc, &vtEmpty,
		SWC_DESKTOP, &lhwnd,
		SWFO_NEEDDISPATCH, &spdisp);

	CComPtr<IShellBrowser> spBrowser;
	CComQIPtr<IServiceProvider>(spdisp)->
		QueryService(SID_STopLevelBrowser,
			IID_PPV_ARGS(&spBrowser));

	CComPtr<IShellView> spView;
	spBrowser->QueryActiveShellView(&spView);

	spView->QueryInterface(riid, ppv);
}

// https://devblogs.microsoft.com/oldnewthing/20131118-00/?p=2643
void GetDesktopAutomationObject(REFIID riid, void** ppv)
{
	CComPtr<IShellView> spsv;
	FindDesktopFolderView(IID_PPV_ARGS(&spsv));
	CComPtr<IDispatch> spdispView;
	spsv->GetItemObject(SVGIO_BACKGROUND, IID_PPV_ARGS(&spdispView));
	spdispView->QueryInterface(riid, ppv);
}

void ShellExecuteFromExplorer(
	PCWSTR pszFile,
	PCWSTR pszParameters = nullptr,
	PCWSTR pszDirectory = nullptr,
	PCWSTR pszOperation = nullptr,
	int nShowCmd = SW_SHOWNORMAL) {
	CComPtr<IShellFolderViewDual> spFolderView;
	GetDesktopAutomationObject(IID_PPV_ARGS(&spFolderView));
	CComPtr<IDispatch> spdispShell;
	spFolderView->get_Application(&spdispShell);
	CComQIPtr<IShellDispatch2>(spdispShell)
		->ShellExecute(CComBSTR(pszFile),
			CComVariant(pszParameters ? pszParameters : L""),
			CComVariant(pszDirectory ? pszDirectory : L""),
			CComVariant(pszOperation ? pszOperation : L""),
			CComVariant(nShowCmd));
}

// https://devblogs.microsoft.com/oldnewthing/20040520-00/?p=39243
class CCoInitialize {
public:
	CCoInitialize() : m_hr(CoInitialize(nullptr)) { }
	~CCoInitialize() { if (SUCCEEDED(m_hr)) CoUninitialize(); }
	operator HRESULT() const { return m_hr; }
	HRESULT m_hr;
};

extern "C" JNIEXPORT void JNICALL Java_net_runelite_launcher_FilePermissionManager_unelevate(JNIEnv *env, jclass clazz, jstring pathObj, jstring argsObj) {
	const jchar *pathString = env->GetStringChars(pathObj, nullptr);
	const jchar *argsString = env->GetStringChars(argsObj, nullptr);

	CCoInitialize init;
	ShellExecuteFromExplorer(reinterpret_cast<const wchar_t *>(pathString),	reinterpret_cast<const wchar_t *>(argsString), L"", L"", SW_SHOWNORMAL);

	env->ReleaseStringChars(pathObj, pathString);
	env->ReleaseStringChars(argsObj, argsString);
}