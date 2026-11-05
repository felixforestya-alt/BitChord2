#define _WIN32_WINNT 0x0A00
#include <jni.h>

#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <audioclient.h>
#include <dwmapi.h>
#include <functiondiscoverykeys_devpkey.h>
#include <mmdeviceapi.h>
#include <propvarutil.h>
#include <windowsx.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <memory>
#include <cmath>
#include <cstdint>
#include <cwchar>
#include <cwctype>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

namespace {

HWND g_window = nullptr;
WNDPROC g_original_proc = nullptr;

// The style and rectangle the window had before it went borderless fullscreen, kept so exiting can
// put the caption window back exactly where it was.
LONG_PTR g_fullscreen_style = 0;
RECT g_fullscreen_rect{};
bool g_fullscreen_active = false;
// The oversized monitor rectangle fullscreen is pinned to; WM_WINDOWPOSCHANGING forces every move
// and resize back to it so nothing — AWT, the shell, the taskbar raising itself — can uncover it.
RECT g_fullscreen_target{};

// Posted from Java to start a caption drag on the thread that owns the window; see frame_proc.
constexpr UINT WM_BITCHORD_DRAG = WM_APP + 0x42;
// SC_MOVE through the caption: the same move loop a real title bar starts.
constexpr WPARAM SC_DRAGMOVE = SC_MOVE | HTCAPTION;

struct FindContext {
    const wchar_t* title;
    HWND found;
};

BOOL CALLBACK find_own_window(HWND window, LPARAM param) {
    auto* context = reinterpret_cast<FindContext*>(param);
    DWORD pid = 0;
    GetWindowThreadProcessId(window, &pid);
    if (pid != GetCurrentProcessId() || !IsWindowVisible(window)) return TRUE;

    wchar_t buffer[256] = {};
    GetWindowTextW(window, buffer, 255);
    if (wcscmp(buffer, context->title) != 0) return TRUE;
    context->found = window;
    return FALSE;
}

HWND find_window(JNIEnv* env, jstring title) {
    if (g_window != nullptr && IsWindow(g_window)) return g_window;
    const jchar* chars = env->GetStringChars(title, nullptr);
    const jsize length = env->GetStringLength(title);
    std::wstring wanted(reinterpret_cast<const wchar_t*>(chars), static_cast<size_t>(length));
    env->ReleaseStringChars(title, chars);
    FindContext context{wanted.c_str(), nullptr};
    EnumWindows(find_own_window, reinterpret_cast<LPARAM>(&context));
    return context.found;
}

int resize_border(HWND window, bool horizontal) {
    const UINT dpi = GetDpiForWindow(window);
    const int frame = GetSystemMetricsForDpi(horizontal ? SM_CXFRAME : SM_CYFRAME, dpi);
    const int padding = GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi);
    return frame + padding;
}

LRESULT CALLBACK frame_proc(HWND window, UINT message, WPARAM wparam, LPARAM lparam) {
    if (message == WM_NCCALCSIZE && wparam == TRUE) {
        // Keep the native overlapped-window styles but give the full surface to Compose. DWM still
        // owns the outer frame, shadow and transitions; only the standard caption is removed.
        if (IsZoomed(window)) {
            // A frameless client normally expands to the maximized window rectangle, which can
            // extend a few pixels into a taskbar docked at the top of the monitor. Use the monitor
            // work area for the client portion so the first row is never hidden behind the taskbar.
            MONITORINFO monitor{sizeof(MONITORINFO)};
            const HMONITOR handle = MonitorFromWindow(window, MONITOR_DEFAULTTONEAREST);
            if (handle != nullptr && GetMonitorInfoW(handle, &monitor) != FALSE) {
                auto* sizes = reinterpret_cast<NCCALCSIZE_PARAMS*>(lparam);
                sizes->rgrc[0] = monitor.rcWork;
            }
        }
        return 0;
    }

    if (message == WM_WINDOWPOSCHANGING && g_fullscreen_active && lparam != 0) {
        // While fullscreen, every attempted move or resize — AWT reasserting its own bounds, the
        // taskbar raising itself, a focus change — is rewritten to the pinned, oversized monitor
        // rectangle at the top of the z-order. This is what keeps the taskbar from peeking back.
        auto* pos = reinterpret_cast<WINDOWPOS*>(lparam);
        pos->hwndInsertAfter = HWND_TOPMOST;
        pos->flags &= ~static_cast<UINT>(SWP_NOZORDER | SWP_NOMOVE | SWP_NOSIZE);
        pos->x = g_fullscreen_target.left;
        pos->y = g_fullscreen_target.top;
        pos->cx = g_fullscreen_target.right - g_fullscreen_target.left;
        pos->cy = g_fullscreen_target.bottom - g_fullscreen_target.top;
        return 0;
    }

    if (message == WM_NCHITTEST && !IsZoomed(window) && !g_fullscreen_active) {
        POINT point{GET_X_LPARAM(lparam), GET_Y_LPARAM(lparam)};
        RECT bounds{};
        GetWindowRect(window, &bounds);
        const int border_x = resize_border(window, true);
        const int border_y = resize_border(window, false);
        const bool left = point.x < bounds.left + border_x;
        const bool right = point.x >= bounds.right - border_x;
        const bool top = point.y < bounds.top + border_y;
        const bool bottom = point.y >= bounds.bottom - border_y;
        if (top && left) return HTTOPLEFT;
        if (top && right) return HTTOPRIGHT;
        if (bottom && left) return HTBOTTOMLEFT;
        if (bottom && right) return HTBOTTOMRIGHT;
        if (left) return HTLEFT;
        if (right) return HTRIGHT;
        if (top) return HTTOP;
        if (bottom) return HTBOTTOM;
    }

    if (message == WM_BITCHORD_DRAG) {
        // Windows moves the window itself from here, as it does for a system caption: DWM slides
        // the composed surface, so nothing is exposed to be filled with the class brush (the white
        // band an AWT setLocation per mouse event left along the edges), and Aero Snap and
        // drag-to-restore come with it. The press that asked for this may already be over; a move
        // loop started without the button down would follow the cursor until the next click.
        const int primary = GetSystemMetrics(SM_SWAPBUTTON) ? VK_RBUTTON : VK_LBUTTON;
        if ((GetAsyncKeyState(primary) & 0x8000) == 0) return 0;
        // AWT captured the mouse on the press; the move loop needs it back.
        ReleaseCapture();
        DefWindowProcW(window, WM_SYSCOMMAND, SC_DRAGMOVE, 0);
        // The loop ate the release. AWT, and Compose behind it, still hold the press that started
        // the drag and would take the next one for part of it: a double click on the caption
        // never became one, and the first click anywhere after a drag went missing. Hand the
        // release back where the pointer now is.
        POINT cursor{};
        if (GetCursorPos(&cursor) != FALSE && ScreenToClient(window, &cursor) != FALSE) {
            PostMessageW(window, WM_LBUTTONUP, 0, MAKELPARAM(cursor.x, cursor.y));
        }
        return 0;
    }

    WNDPROC original = g_original_proc;
    if (message == WM_NCDESTROY) {
        g_window = nullptr;
        g_original_proc = nullptr;
    }
    return original != nullptr
        ? CallWindowProcW(original, window, message, wparam, lparam)
        : DefWindowProcW(window, message, wparam, lparam);
}

bool install_frame(HWND window) {
    if (window == nullptr) return false;
    if (window == g_window && g_original_proc != nullptr) return true;

    SetLastError(0);
    auto previous = reinterpret_cast<WNDPROC>(
        SetWindowLongPtrW(window, GWLP_WNDPROC, reinterpret_cast<LONG_PTR>(frame_proc)));
    if (previous == nullptr && GetLastError() != 0) return false;

    g_window = window;
    g_original_proc = previous;

    LONG_PTR style = GetWindowLongPtrW(window, GWL_STYLE);
    // No WS_SYSMENU: with it, DWM paints its own minimise / maximise / close into the frame
    // extended under a Mica or Acrylic window — a second set of buttons beside the app's own for
    // the first frames after launch. The app's buttons post the same system commands.
    style &= ~static_cast<LONG_PTR>(WS_POPUP | WS_SYSMENU);
    style |= WS_OVERLAPPED | WS_CAPTION | WS_THICKFRAME |
        WS_MINIMIZEBOX | WS_MAXIMIZEBOX;
    SetWindowLongPtrW(window, GWL_STYLE, style);

    // Ask DWM to own the non-client rendering and Windows 11 corner policy. Unlike an AWT shape,
    // these do not clip the surface and are automatically disabled when maximized or snapped.
    const DWMNCRENDERINGPOLICY policy = DWMNCRP_ENABLED;
    DwmSetWindowAttribute(
        window, DWMWA_NCRENDERING_POLICY, &policy, sizeof(policy));
#ifndef DWMWA_WINDOW_CORNER_PREFERENCE
#define DWMWA_WINDOW_CORNER_PREFERENCE 33
#endif
    const int rounded = 2;  // DWMWCP_ROUND; kept numeric so older SDKs can still build the bridge.
    DwmSetWindowAttribute(
        window, static_cast<DWMWINDOWATTRIBUTE>(DWMWA_WINDOW_CORNER_PREFERENCE),
        &rounded, sizeof(rounded));

    MARGINS margins{1, 1, 1, 1};
    DwmExtendFrameIntoClientArea(window, &margins);
    SetWindowPos(
        window, nullptr, 0, 0, 0, 0,
        SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
    return true;
}

// Windows 11 22H2's system backdrop attribute and its values; numeric so older SDKs still build.
constexpr DWORD BACKDROP_ATTRIBUTE = 38;  // DWMWA_SYSTEMBACKDROP_TYPE
constexpr DWORD DARK_MODE_ATTRIBUTE = 20;  // DWMWA_USE_IMMERSIVE_DARK_MODE
constexpr int BACKDROP_NONE = 1;  // DWMSBT_NONE

/**
 * Puts DWM's own material behind the window: 2 Mica, 3 Acrylic, anything else none. The frame is
 * extended over the whole client so the material reaches everywhere Compose leaves transparent;
 * without a material it goes back to the one-pixel margin the frame needs for its shadow.
 *
 * Dark mode is what makes the material a dark tint rather than a light one. False when the system
 * has no backdrop attribute (before Windows 11 22H2), and the caller keeps the window opaque.
 */
bool set_backdrop(HWND window, int kind) {
    if (window == nullptr || !IsWindow(window)) return false;
    const BOOL dark = TRUE;
    DwmSetWindowAttribute(
        window, static_cast<DWMWINDOWATTRIBUTE>(DARK_MODE_ATTRIBUTE), &dark, sizeof(dark));
    const bool material = kind == 2 || kind == 3;
    const int type = material ? kind : BACKDROP_NONE;
    const HRESULT applied = DwmSetWindowAttribute(
        window, static_cast<DWMWINDOWATTRIBUTE>(BACKDROP_ATTRIBUTE), &type, sizeof(type));
    if (FAILED(applied)) return false;
    MARGINS margins = material ? MARGINS{-1, -1, -1, -1} : MARGINS{1, 1, 1, 1};
    DwmExtendFrameIntoClientArea(window, &margins);
    return true;
}

bool send_system_command(UINT command) {
    HWND window = g_window;
    return window != nullptr && IsWindow(window) &&
        PostMessageW(window, WM_SYSCOMMAND, command, 0) != 0;
}

/**
 * Fills the whole monitor — taskbar included — with a frameless window above it. A maximized window
 * can never cover a taskbar told to stay on top, so this is what the player's full-screen button
 * and F11 ask for. The window's own style and rectangle survive the trip and come back on exit.
 *
 * The popup is pushed a few pixels past every edge of the monitor, so DWM's rounded-corner cutout
 * and any sub-pixel seam where the surface used to stop land off-screen rather than showing the
 * desktop through them. WM_WINDOWPOSCHANGING pins that rectangle and the topmost band from then on.
 */
bool set_fullscreen(HWND window, bool on) {
    if (window == nullptr || !IsWindow(window)) return false;

    if (on) {
        if (g_fullscreen_active) return true;
        MONITORINFO monitor{sizeof(MONITORINFO)};
        const HMONITOR handle = MonitorFromWindow(window, MONITOR_DEFAULTTONEAREST);
        if (handle == nullptr || GetMonitorInfoW(handle, &monitor) == FALSE) return false;

        g_fullscreen_style = GetWindowLongPtrW(window, GWL_STYLE);
        if (GetWindowRect(window, &g_fullscreen_rect) == FALSE) return false;
        constexpr int OVERSHOOT = 8;
        g_fullscreen_target = RECT{
            monitor.rcMonitor.left - OVERSHOOT,
            monitor.rcMonitor.top - OVERSHOOT,
            monitor.rcMonitor.right + OVERSHOOT,
            monitor.rcMonitor.bottom + OVERSHOOT,
        };

        // Corners squared off and no extended frame: the material and the rounded-corner mask are
        // what left the see-through ring around the edges. The flag goes up first so the enabling
        // SetWindowPos below is itself clamped to the pinned rectangle.
        g_fullscreen_active = true;
        const int square = 1;  // DWMWCP_DONOTROUND
        DwmSetWindowAttribute(
            window, static_cast<DWMWINDOWATTRIBUTE>(DWMWA_WINDOW_CORNER_PREFERENCE),
            &square, sizeof(square));
        const MARGINS no_frame{0, 0, 0, 0};
        DwmExtendFrameIntoClientArea(window, &no_frame);

        SetWindowLongPtrW(window, GWL_STYLE, WS_POPUP | (g_fullscreen_style & WS_VISIBLE));
        SetWindowPos(
            window, HWND_TOPMOST,
            g_fullscreen_target.left, g_fullscreen_target.top,
            g_fullscreen_target.right - g_fullscreen_target.left,
            g_fullscreen_target.bottom - g_fullscreen_target.top,
            SWP_FRAMECHANGED | SWP_SHOWWINDOW);
        return true;
    }

    if (!g_fullscreen_active) return true;
    g_fullscreen_active = false;
    const int rounded = 2;  // DWMWCP_ROUND
    DwmSetWindowAttribute(
        window, static_cast<DWMWINDOWATTRIBUTE>(DWMWA_WINDOW_CORNER_PREFERENCE),
        &rounded, sizeof(rounded));

    SetWindowLongPtrW(window, GWL_STYLE, g_fullscreen_style);
    SetWindowPos(
        window, HWND_NOTOPMOST,
        g_fullscreen_rect.left, g_fullscreen_rect.top,
        g_fullscreen_rect.right - g_fullscreen_rect.left,
        g_fullscreen_rect.bottom - g_fullscreen_rect.top,
        SWP_FRAMECHANGED | SWP_SHOWWINDOW);
    // Hand the frame's own shadow margin and any material back; DesktopWindowBackdrop.apply()
    // re-asserts the chosen material over the top of this on the Kotlin side.
    const MARGINS shadow_frame{1, 1, 1, 1};
    DwmExtendFrameIntoClientArea(window, &shadow_frame);
    return true;
}

// ---- Shared-mode Windows audio --------------------------------------------
//
// The playback thread never calls into WASAPI. It puts its samples in a short ring here, and a
// render thread of our own moves them on to the endpoint. After the computer had slept, a monitor's
// HDMI audio could leave a WASAPI call blocked for good; made on the playback thread under the one
// lock everything shared, that call froze the player — no other track would load, and opening the
// output list froze the window as well. Now a render thread that stops answering is left behind:
// a fresh one takes over with a new stream, and the old one, if it ever returns, sees it is no
// longer the current one and leaves without touching anything.
//
// What Kotlin asks for (g_audio_wanted: an endpoint id, a legacy device name, or blank for the
// system default) is kept apart from the endpoint actually open: Windows' default moves when the
// user picks another output, a chosen DAC can be unplugged and plugged back in, and an
// IMMNotificationClient flags those moments for the render thread to follow.

std::mutex g_audio_mutex;
std::condition_variable g_audio_room;
std::condition_variable g_audio_opened;

// The format Kotlin opened with; kept across rebuilt streams so they take the same samples.
int g_audio_rate = 0;
UINT32 g_audio_channels = 0;
UINT32 g_audio_bytes_per_sample = 0;
bool g_audio_float = false;
bool g_audio_open = false;
// Whether playback wants the stream running.
bool g_audio_started = false;
bool g_audio_flush = false;

// Samples waiting for the device, already scaled and limited.
std::vector<float> g_audio_ring;
size_t g_audio_ring_read = 0;
size_t g_audio_ring_count = 0;

// Frames heard so far, across every stream since opening: Kotlin's position follows this.
uint64_t g_audio_played = 0;
UINT32 g_audio_padding = 0;
UINT32 g_audio_buffer_frames = 0;
std::wstring g_audio_wanted;
std::wstring g_audio_device_name;
std::wstring g_audio_error;

// Which render thread is the current one; a thread whose number is not this one only cleans up.
uint64_t g_audio_generation = 0;
// When the current render thread last went round its loop, by GetTickCount64.
std::atomic<ULONGLONG> g_audio_heartbeat{0};
// Set by the open: whether the new thread got a stream (1), could not (2), or has not said (0).
int g_audio_open_result = 0;

std::atomic<bool> g_audio_reroute{false};
// Streams rebuilt because the device stopped taking samples or its thread stopped answering.
std::atomic<int> g_audio_stalls{0};
bool g_audio_watching = false;
JavaVM* g_vm = nullptr;

constexpr UINT32 RING_MS = 60;
constexpr REFERENCE_TIME DEVICE_BUFFER_HNS = 2'000'000;
constexpr ULONGLONG DEVICE_STALL_MS = 1'500;
constexpr ULONGLONG THREAD_STALL_MS = 3'000;

class ComScope {
public:
    ComScope() : result(CoInitializeEx(nullptr, COINIT_MULTITHREADED)) {}
    ~ComScope() {
        if (result == S_OK || result == S_FALSE) CoUninitialize();
    }
    bool ready() const { return SUCCEEDED(result) || result == RPC_E_CHANGED_MODE; }
    HRESULT result;
};

std::wstring audio_error(const wchar_t* operation, HRESULT result) {
    wchar_t code[32] = {};
    swprintf(code, 31, L"0x%08lX", static_cast<unsigned long>(result));
    std::wstring text = operation;
    text += L" failed (";
    text += code;
    text += L")";
    return text;
}

/** Tells Kotlin the device list changed. Never enumerates here: the callback thread must not. */
void notify_devices_changed() {
    JavaVM* vm = g_vm;
    if (vm == nullptr) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) return;
        attached = true;
    }
    jclass type = env->FindClass("com/music/bitchord/desktop/DesktopWindowsAudio");
    if (type != nullptr) {
        jmethodID method = env->GetStaticMethodID(type, "onDevicesChanged", "()V");
        if (method != nullptr) env->CallStaticVoidMethod(type, method);
        env->DeleteLocalRef(type);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (attached) vm->DetachCurrentThread();
}

/** Lives as long as the process; registered once, on the enumerator kept for it. */
class EndpointWatcher final : public IMMNotificationClient {
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID id, void** out) override {
        if (out == nullptr) return E_POINTER;
        if (id == __uuidof(IUnknown) || id == __uuidof(IMMNotificationClient)) {
            *out = static_cast<IMMNotificationClient*>(this);
            return S_OK;
        }
        *out = nullptr;
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return 1; }
    ULONG STDMETHODCALLTYPE Release() override { return 1; }

    HRESULT STDMETHODCALLTYPE OnDefaultDeviceChanged(EDataFlow flow, ERole, LPCWSTR) override {
        if (flow == eRender) g_audio_reroute = true;
        return S_OK;
    }
    HRESULT STDMETHODCALLTYPE OnDeviceAdded(LPCWSTR) override { return changed(); }
    HRESULT STDMETHODCALLTYPE OnDeviceRemoved(LPCWSTR) override { return changed(); }
    HRESULT STDMETHODCALLTYPE OnDeviceStateChanged(LPCWSTR, DWORD) override { return changed(); }
    HRESULT STDMETHODCALLTYPE OnPropertyValueChanged(LPCWSTR, const PROPERTYKEY) override { return S_OK; }

private:
    static HRESULT changed() {
        g_audio_reroute = true;
        notify_devices_changed();
        return S_OK;
    }
};

EndpointWatcher g_audio_watcher;

/** A fresh enumerator for the calling thread; the first one made also carries the watcher. */
IMMDeviceEnumerator* new_enumerator() {
    IMMDeviceEnumerator* enumerator = nullptr;
    if (FAILED(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr, CLSCTX_ALL,
                                __uuidof(IMMDeviceEnumerator), reinterpret_cast<void**>(&enumerator)))) {
        return nullptr;
    }
    static std::once_flag watched;
    std::call_once(watched, [enumerator] {
        // Kept for the life of the process: the notifications come through this one.
        static CO_MTA_USAGE_COOKIE mta = nullptr;
        CoIncrementMTAUsage(&mta);
        enumerator->AddRef();
        g_audio_watching = SUCCEEDED(enumerator->RegisterEndpointNotificationCallback(&g_audio_watcher));
    });
    return enumerator;
}

std::wstring property_string(IMMDevice* device, const PROPERTYKEY& key) {
    IPropertyStore* store = nullptr;
    if (FAILED(device->OpenPropertyStore(STGM_READ, &store)) || store == nullptr) return L"";
    PROPVARIANT value;
    PropVariantInit(&value);
    std::wstring result;
    if (SUCCEEDED(store->GetValue(key, &value)) && value.vt == VT_LPWSTR && value.pwszVal != nullptr) {
        result = value.pwszVal;
    }
    PropVariantClear(&value);
    store->Release();
    return result;
}

std::wstring endpoint_id(IMMDevice* device) {
    LPWSTR id = nullptr;
    std::wstring result;
    if (SUCCEEDED(device->GetId(&id)) && id != nullptr) {
        result = id;
        CoTaskMemFree(id);
    }
    return result;
}

std::wstring lower(std::wstring value) {
    std::transform(value.begin(), value.end(), value.begin(), [](wchar_t c) {
        return static_cast<wchar_t>(towlower(c));
    });
    return value;
}

/** Endpoint ids look like `{0.0.0.00000000}.{guid}`; anything else is a name from before them. */
bool is_endpoint_id(const std::wstring& value) {
    return value.size() > 2 && value[0] == L'{' && value.find(L"}.{") != std::wstring::npos;
}

IMMDevice* default_device(IMMDeviceEnumerator* enumerator) {
    IMMDevice* device = nullptr;
    return SUCCEEDED(enumerator->GetDefaultAudioEndpoint(eRender, eMultimedia, &device)) ? device : nullptr;
}

bool is_active(IMMDevice* device) {
    DWORD state = 0;
    return SUCCEEDED(device->GetState(&state)) && state == DEVICE_STATE_ACTIVE;
}

/**
 * A device stored by name before ids were used. Only an exact name counts: the old substring match
 * let every "Speakers (…)" request land on whichever endpoint described itself as "Speakers" first.
 */
IMMDevice* device_named(IMMDeviceEnumerator* enumerator, const std::wstring& wanted) {
    IMMDeviceCollection* devices = nullptr;
    if (FAILED(enumerator->EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE, &devices)) || devices == nullptr) {
        return nullptr;
    }
    const std::wstring needle = lower(wanted);
    UINT count = 0;
    devices->GetCount(&count);
    IMMDevice* match = nullptr;
    for (UINT index = 0; index < count && match == nullptr; ++index) {
        IMMDevice* candidate = nullptr;
        if (FAILED(devices->Item(index, &candidate)) || candidate == nullptr) continue;
        if (lower(property_string(candidate, PKEY_Device_FriendlyName)) == needle) {
            match = candidate;
        } else {
            candidate->Release();
        }
    }
    devices->Release();
    return match;
}

/**
 * Where [wanted] should play right now: that endpoint while it is plugged in, otherwise the
 * system default — headphones get unplugged, and that should not stop playback.
 */
IMMDevice* resolve_device(IMMDeviceEnumerator* enumerator, const std::wstring& wanted) {
    if (enumerator == nullptr) return nullptr;
    if (!wanted.empty()) {
        IMMDevice* device = nullptr;
        if (is_endpoint_id(wanted)) {
            if (FAILED(enumerator->GetDevice(wanted.c_str(), &device))) device = nullptr;
        } else {
            device = device_named(enumerator, wanted);
        }
        if (device != nullptr && is_active(device)) return device;
        if (device != nullptr) device->Release();
    }
    return default_device(enumerator);
}

/** One render thread's own stream: nothing in it is shared with any other thread. */
struct Stream {
    IAudioClient* client = nullptr;
    IAudioRenderClient* render = nullptr;
    UINT32 buffer_frames = 0;
    std::wstring endpoint;
    std::wstring name;
    bool running = false;

    void release() {
        if (client != nullptr) client->Stop();
        if (render != nullptr) render->Release();
        if (client != nullptr) client->Release();
        client = nullptr;
        render = nullptr;
        buffer_frames = 0;
        running = false;
    }
};

/** Builds a stream on [device] in the opened format. Empty on success, else what failed. */
std::wstring open_stream(Stream& stream, IMMDevice* device, int rate, UINT32 channels, UINT32 bytes, bool floating) {
    HRESULT status = device->Activate(__uuidof(IAudioClient), CLSCTX_ALL, nullptr,
                                      reinterpret_cast<void**>(&stream.client));
    if (FAILED(status) || stream.client == nullptr) {
        stream.client = nullptr;
        return audio_error(L"Opening the audio endpoint", status);
    }
    WAVEFORMATEX format{};
    format.wFormatTag = floating ? WAVE_FORMAT_IEEE_FLOAT : WAVE_FORMAT_PCM;
    format.nChannels = static_cast<WORD>(channels);
    format.nSamplesPerSec = static_cast<DWORD>(rate);
    format.wBitsPerSample = static_cast<WORD>(bytes * 8);
    format.nBlockAlign = static_cast<WORD>(channels * bytes);
    format.nAvgBytesPerSec = format.nSamplesPerSec * format.nBlockAlign;
    format.cbSize = 0;
#ifndef AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM
#define AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM 0x80000000
#endif
#ifndef AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY
#define AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY 0x08000000
#endif
    const DWORD flags = AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY;
    // 200 ms keeps network/decoder jitter away from the device without making controls sluggish.
    status = stream.client->Initialize(AUDCLNT_SHAREMODE_SHARED, flags, DEVICE_BUFFER_HNS, 0, &format, nullptr);
    if (FAILED(status)) {
        stream.release();
        return audio_error(L"Starting WASAPI shared mode", status);
    }
    status = stream.client->GetBufferSize(&stream.buffer_frames);
    if (SUCCEEDED(status)) {
        status = stream.client->GetService(__uuidof(IAudioRenderClient), reinterpret_cast<void**>(&stream.render));
    }
    if (FAILED(status) || stream.render == nullptr) {
        stream.release();
        return audio_error(L"Starting the WASAPI render stream", status);
    }
    stream.endpoint = endpoint_id(device);
    stream.name = property_string(device, PKEY_Device_FriendlyName);
    return L"";
}

/** Converts [frames] of scaled float samples into what the endpoint was opened with. */
void convert_into(BYTE* target, const float* samples, size_t values, UINT32 bytes, bool floating) {
    if (floating) {
        std::memcpy(target, samples, values * sizeof(float));
    } else if (bytes == 2) {
        auto* output = reinterpret_cast<int16_t*>(target);
        for (size_t index = 0; index < values; ++index) output[index] = static_cast<int16_t>(samples[index] * 32767.0f);
    } else {
        auto* output = reinterpret_cast<int32_t*>(target);
        for (size_t index = 0; index < values; ++index) {
            output[index] = static_cast<int32_t>(static_cast<double>(samples[index]) * 2147483647.0);
        }
    }
}

/**
 * The render thread. Takes from the ring whatever the device has room for, every few
 * milliseconds; rebuilds its stream when the endpoint moves, fails or stops taking samples; and
 * with no endpoint at all lets the ring run out in real time, so playback neither races nor waits.
 */
DWORD WINAPI render_thread(LPVOID parameter) {
    const uint64_t generation = reinterpret_cast<uint64_t>(parameter);
    ComScope com;
    IMMDeviceEnumerator* enumerator = com.ready() ? new_enumerator() : nullptr;
    Stream stream;
    uint64_t submitted = 0;
    uint64_t played_base = 0;
    UINT32 last_padding = 0;
    ULONGLONG full_since = 0;
    ULONGLONG retry_at = 0;
    ULONGLONG idle_from = 0;
    bool reported = false;
    std::vector<float> chunk;

    auto current = [generation] { return g_audio_generation == generation; };
    auto rebuild = [&](bool stalled) {
        played_base += submitted - std::min<uint64_t>(last_padding, submitted);
        submitted = 0;
        last_padding = 0;
        stream.release();
        if (stalled) g_audio_stalls++;
    };

    for (;;) {
        const ULONGLONG now = GetTickCount64();
        if (g_audio_generation == generation) g_audio_heartbeat = now;
        std::wstring wanted;
        int rate;
        UINT32 channels, bytes;
        bool floating, started, flush;
        {
            std::lock_guard<std::mutex> guard(g_audio_mutex);
            if (!current() || !g_audio_open) break;
            wanted = g_audio_wanted;
            rate = g_audio_rate;
            channels = g_audio_channels;
            bytes = g_audio_bytes_per_sample;
            floating = g_audio_float;
            started = g_audio_started;
            flush = g_audio_flush;
            g_audio_flush = false;
        }

        if (g_audio_reroute.exchange(false) && stream.client != nullptr) {
            IMMDevice* device = resolve_device(enumerator, wanted);
            const bool same = device != nullptr && endpoint_id(device) == stream.endpoint;
            if (device != nullptr) device->Release();
            if (!same) rebuild(false);
        }

        if (stream.client == nullptr && now >= retry_at) {
            IMMDevice* device = resolve_device(enumerator, wanted);
            std::wstring failure = device == nullptr ? L"Windows has no audio endpoint to play on"
                                                     : open_stream(stream, device, rate, channels, bytes, floating);
            if (device != nullptr) device->Release();
            std::lock_guard<std::mutex> guard(g_audio_mutex);
            if (!current()) {
                stream.release();
                break;
            }
            if (failure.empty()) {
                g_audio_buffer_frames = stream.buffer_frames;
                g_audio_device_name = stream.name;
                if (!reported) g_audio_open_result = 1;
            } else {
                g_audio_error = failure;
                retry_at = now + 1'000;
                if (!reported) g_audio_open_result = 2;
            }
            if (!reported) {
                reported = true;
                g_audio_opened.notify_all();
            }
        }

        if (stream.client != nullptr && flush) {
            stream.client->Stop();
            stream.client->Reset();
            played_base += submitted - std::min<uint64_t>(last_padding, submitted);
            submitted = 0;
            last_padding = 0;
            stream.running = false;
        }
        if (stream.client != nullptr && started != stream.running) {
            if (started) stream.client->Start(); else stream.client->Stop();
            stream.running = started;
            full_since = 0;
        }

        UINT32 room = 0;
        if (stream.client != nullptr) {
            UINT32 padding = 0;
            const HRESULT status = stream.client->GetCurrentPadding(&padding);
            if (FAILED(status)) {
                rebuild(status != AUDCLNT_E_DEVICE_INVALIDATED);
            } else {
                last_padding = padding;
                room = padding < stream.buffer_frames ? stream.buffer_frames - padding : 0;
                // Full, playing, and not one frame taken in all this time: a stream that has stopped.
                if (started && room == 0) {
                    if (full_since == 0) full_since = now;
                    else if (now - full_since >= DEVICE_STALL_MS) rebuild(true);
                } else {
                    full_since = 0;
                }
            }
        }

        size_t frames = 0;
        if (started) {
            std::lock_guard<std::mutex> guard(g_audio_mutex);
            if (!current()) break;
            const size_t queued = g_audio_ring_count / std::max<UINT32>(channels, 1);
            if (stream.client != nullptr) {
                frames = std::min<size_t>(queued, room);
                idle_from = 0;
            } else {
                // No endpoint: the samples go by in real time.
                if (idle_from == 0) idle_from = now;
                frames = std::min<size_t>(queued, static_cast<size_t>((now - idle_from) * rate / 1000));
                if (frames > 0) idle_from = now;
            }
            const size_t values = frames * channels;
            chunk.resize(values);
            for (size_t index = 0; index < values; ++index) {
                chunk[index] = g_audio_ring[(g_audio_ring_read + index) % g_audio_ring.size()];
            }
            if (values > 0) {
                g_audio_ring_read = (g_audio_ring_read + values) % g_audio_ring.size();
                g_audio_ring_count -= values;
                g_audio_room.notify_all();
            }
            if (stream.client == nullptr) played_base += frames;
        }

        if (frames > 0 && stream.client != nullptr) {
            BYTE* target = nullptr;
            HRESULT status = stream.render->GetBuffer(static_cast<UINT32>(frames), &target);
            if (SUCCEEDED(status) && target != nullptr) {
                convert_into(target, chunk.data(), frames * channels, bytes, floating);
                status = stream.render->ReleaseBuffer(static_cast<UINT32>(frames), 0);
            }
            if (FAILED(status)) {
                rebuild(status != AUDCLNT_E_DEVICE_INVALIDATED);
            } else {
                submitted += frames;
                last_padding += static_cast<UINT32>(frames);
            }
        }

        {
            std::lock_guard<std::mutex> guard(g_audio_mutex);
            if (!current()) break;
            g_audio_played = played_base + submitted - std::min<uint64_t>(last_padding, submitted);
            g_audio_padding = stream.client != nullptr ? last_padding : 0;
        }
        Sleep(3);
    }
    stream.release();
    if (enumerator != nullptr) enumerator->Release();
    return 0;
}

/** Starts a render thread as the current one. Under the lock. */
void start_render_thread_locked() {
    const uint64_t generation = ++g_audio_generation;
    g_audio_heartbeat = GetTickCount64();
    HANDLE thread = CreateThread(nullptr, 0, render_thread, reinterpret_cast<LPVOID>(generation), 0, nullptr);
    if (thread != nullptr) {
        SetThreadPriority(thread, THREAD_PRIORITY_TIME_CRITICAL);
        CloseHandle(thread);
    }
}

/** The current render thread has stopped answering: leave it behind and start another. Under the lock. */
void replace_stuck_thread_locked() {
    if (!g_audio_open || GetTickCount64() - g_audio_heartbeat.load() < THREAD_STALL_MS) return;
    g_audio_stalls++;
    g_audio_padding = 0;
    start_render_thread_locked();
}

void close_audio_locked() {
    g_audio_open = false;
    ++g_audio_generation;
    g_audio_started = false;
    g_audio_ring.clear();
    g_audio_ring_read = 0;
    g_audio_ring_count = 0;
    g_audio_played = 0;
    g_audio_padding = 0;
    g_audio_buffer_frames = 0;
    g_audio_device_name.clear();
    g_audio_room.notify_all();
}

int open_audio(const std::wstring& wanted, int sample_rate, int channels, int bytes_per_sample, bool floating) {
    std::unique_lock<std::mutex> guard(g_audio_mutex);
    close_audio_locked();
    g_audio_error.clear();
    g_audio_wanted = wanted;
    g_audio_rate = sample_rate;
    g_audio_channels = static_cast<UINT32>(channels);
    g_audio_bytes_per_sample = static_cast<UINT32>(bytes_per_sample);
    g_audio_float = floating;
    g_audio_ring.assign(static_cast<size_t>(sample_rate) * channels * RING_MS / 1000, 0.0f);
    g_audio_open = true;
    g_audio_started = true;
    g_audio_open_result = 0;
    start_render_thread_locked();
    const bool answered = g_audio_opened.wait_for(guard, std::chrono::seconds(5), [] { return g_audio_open_result != 0; });
    if (!answered || g_audio_open_result != 1) {
        if (g_audio_error.empty()) g_audio_error = L"The audio endpoint did not answer";
        close_audio_locked();
        return 0;
    }
    const size_t frames = g_audio_buffer_frames + g_audio_ring.size() / static_cast<size_t>(channels);
    return static_cast<int>(frames * channels * bytes_per_sample);
}

/**
 * Puts [sample_count] samples in the ring, waiting for room as the device plays — which is what
 * keeps playback in time. Never waits on Windows itself; a render thread that has stopped is
 * replaced from here.
 */
int write_audio(const float* samples, int sample_count, float gain) {
    if (samples == nullptr || sample_count <= 0) return 0;
    std::unique_lock<std::mutex> guard(g_audio_mutex);
    if (!g_audio_open || g_audio_channels == 0 || g_audio_ring.empty()) return 0;
    size_t at = 0;
    const size_t total = static_cast<size_t>(sample_count) - static_cast<size_t>(sample_count) % g_audio_channels;
    while (at < total) {
        if (!g_audio_open) break;
        // Stopped, nothing will make room: waiting here would hold the player's thread.
        if (!g_audio_started) break;
        const size_t free_values = g_audio_ring.size() - g_audio_ring_count;
        if (free_values < g_audio_channels) {
            g_audio_room.wait_for(guard, std::chrono::milliseconds(20));
            replace_stuck_thread_locked();
            continue;
        }
        size_t values = std::min(free_values, total - at);
        values -= values % g_audio_channels;
        size_t write = (g_audio_ring_read + g_audio_ring_count) % g_audio_ring.size();
        for (size_t index = 0; index < values; ++index) {
            g_audio_ring[write] = std::clamp(samples[at + index] * gain, -1.0f, 1.0f);
            if (++write == g_audio_ring.size()) write = 0;
        }
        g_audio_ring_count += values;
        at += values;
    }
    return static_cast<int>(at / g_audio_channels * g_audio_channels * g_audio_bytes_per_sample);
}

/** Runs [work] on a thread of its own and gives up waiting after [limit_ms]; false if it did. */
template <typename Work>
bool run_bounded(Work work, DWORD limit_ms) {
    struct Job {
        Work work;
        std::atomic<int> owners{2};
    };
    auto* job = new Job{std::move(work)};
    HANDLE thread = CreateThread(nullptr, 0, [](LPVOID raw) -> DWORD {
        auto* job = static_cast<Job*>(raw);
        {
            ComScope com;
            if (com.ready()) job->work();
        }
        if (--job->owners == 0) delete job;
        return 0;
    }, job, 0, nullptr);
    if (thread == nullptr) {
        delete job;
        return false;
    }
    const bool done = WaitForSingleObject(thread, limit_ms) == WAIT_OBJECT_0;
    CloseHandle(thread);
    if (--job->owners == 0) delete job;
    return done;
}

/**
 * The shared-mode mix rate of the endpoint [wanted] resolves to, or 0. Opening the stream at this
 * rate means the Windows engine has nothing left to resample, so the app's own windowed-sinc
 * converter is the only one the audio ever passes through.
 */
int mix_rate(const std::wstring& wanted) {
    auto rate = std::make_shared<std::atomic<int>>(0);
    run_bounded([wanted, rate] {
        IMMDeviceEnumerator* enumerator = new_enumerator();
        IMMDevice* device = resolve_device(enumerator, wanted);
        if (device != nullptr) {
            IAudioClient* client = nullptr;
            if (SUCCEEDED(device->Activate(__uuidof(IAudioClient), CLSCTX_ALL, nullptr,
                                           reinterpret_cast<void**>(&client))) && client != nullptr) {
                WAVEFORMATEX* mix = nullptr;
                if (SUCCEEDED(client->GetMixFormat(&mix)) && mix != nullptr) {
                    *rate = static_cast<int>(mix->nSamplesPerSec);
                    CoTaskMemFree(mix);
                }
                client->Release();
            }
            device->Release();
        }
        if (enumerator != nullptr) enumerator->Release();
    }, 3'000);
    return *rate;
}

std::mutex g_devices_mutex;
std::vector<std::wstring> g_devices_seen;

jobjectArray list_devices(JNIEnv* env) {
    auto found = std::make_shared<std::vector<std::wstring>>();
    // Asked from the window's thread: a list that will not come leaves the last one in place
    // rather than the window frozen.
    const bool listed = run_bounded([found] {
        IMMDeviceEnumerator* enumerator = new_enumerator();
        IMMDeviceCollection* devices = nullptr;
        if (enumerator != nullptr &&
            SUCCEEDED(enumerator->EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE, &devices)) &&
            devices != nullptr) {
            UINT count = 0;
            devices->GetCount(&count);
            for (UINT index = 0; index < count; ++index) {
                IMMDevice* device = nullptr;
                if (FAILED(devices->Item(index, &device)) || device == nullptr) continue;
                found->push_back(endpoint_id(device));
                found->push_back(property_string(device, PKEY_Device_FriendlyName));
                device->Release();
            }
            devices->Release();
        }
        if (enumerator != nullptr) enumerator->Release();
    }, 2'000);
    std::vector<std::wstring> values;
    {
        std::lock_guard<std::mutex> guard(g_devices_mutex);
        if (listed) g_devices_seen = *found;
        values = g_devices_seen;
    }
    jclass string_type = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(values.size()), string_type, nullptr);
    for (size_t index = 0; index < values.size(); ++index) {
        jstring value = env->NewString(
            reinterpret_cast<const jchar*>(values[index].c_str()), static_cast<jsize>(values[index].size()));
        env->SetObjectArrayElement(result, static_cast<jsize>(index), value);
        env->DeleteLocalRef(value);
    }
    return result;
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsFrame_nativeInstall(
    JNIEnv* env, jclass, jstring title) {
    return install_frame(find_window(env, title)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsFrame_nativeMinimize(JNIEnv*, jclass) {
    return send_system_command(SC_MINIMIZE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsFrame_nativeSetBackdrop(JNIEnv*, jclass, jint kind) {
    return set_backdrop(g_window, static_cast<int>(kind)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsFrame_nativeStartDrag(JNIEnv*, jclass) {
    HWND window = g_window;
    return window != nullptr && IsWindow(window) &&
        PostMessageW(window, WM_BITCHORD_DRAG, 0, 0) != 0
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsFrame_nativeToggleMaximize(JNIEnv*, jclass) {
    HWND window = g_window;
    if (window == nullptr || !IsWindow(window)) return JNI_FALSE;
    return send_system_command(IsZoomed(window) ? SC_RESTORE : SC_MAXIMIZE)
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsFrame_nativeSetFullscreen(JNIEnv*, jclass, jboolean on) {
    return set_fullscreen(g_window, on == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeOpen(
    JNIEnv* env,
    jclass,
    jstring device,
    jint sample_rate,
    jint channels,
    jint bytes_per_sample,
    jboolean floating) {
    if (g_vm == nullptr) env->GetJavaVM(&g_vm);
    const jchar* chars = env->GetStringChars(device, nullptr);
    const jsize length = env->GetStringLength(device);
    std::wstring wanted(reinterpret_cast<const wchar_t*>(chars), static_cast<size_t>(length));
    env->ReleaseStringChars(device, chars);
    return static_cast<jint>(open_audio(wanted, sample_rate, channels, bytes_per_sample, floating == JNI_TRUE));
}

JNIEXPORT jint JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeMixRate(JNIEnv* env, jclass, jstring device) {
    if (g_vm == nullptr) env->GetJavaVM(&g_vm);
    const jchar* chars = env->GetStringChars(device, nullptr);
    const jsize length = env->GetStringLength(device);
    std::wstring wanted(reinterpret_cast<const wchar_t*>(chars), static_cast<size_t>(length));
    env->ReleaseStringChars(device, chars);
    return static_cast<jint>(mix_rate(wanted));
}

JNIEXPORT jobjectArray JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeDevices(JNIEnv* env, jclass) {
    if (g_vm == nullptr) env->GetJavaVM(&g_vm);
    return list_devices(env);
}

JNIEXPORT jint JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeWrite(
    JNIEnv* env, jclass, jfloatArray samples, jint count, jfloat gain) {
    if (samples == nullptr || count <= 0) return 0;
    const jsize available = env->GetArrayLength(samples);
    const jint safe_count = std::min(count, static_cast<jint>(available));
    jboolean copied = JNI_FALSE;
    auto* values = env->GetFloatArrayElements(samples, &copied);
    if (values == nullptr) return 0;
    const int written = write_audio(values, static_cast<int>(safe_count), static_cast<float>(gain));
    env->ReleaseFloatArrayElements(samples, values, JNI_ABORT);
    return static_cast<jint>(written);
}

JNIEXPORT jlong JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeFramesPlayed(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_audio_mutex);
    return static_cast<jlong>(g_audio_played);
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativePause(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_audio_mutex);
    g_audio_started = false;
    g_audio_room.notify_all();
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeResume(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_audio_mutex);
    g_audio_started = true;
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeFlush(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_audio_mutex);
    g_audio_ring_read = 0;
    g_audio_ring_count = 0;
    g_audio_flush = true;
    g_audio_room.notify_all();
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeDrain(JNIEnv*, jclass) {
    // Until what was written has been heard; bounded, so a device that stopped cannot hold it.
    const ULONGLONG until = GetTickCount64() + 2'000;
    while (GetTickCount64() < until) {
        {
            std::lock_guard<std::mutex> guard(g_audio_mutex);
            if (!g_audio_open || (g_audio_ring_count == 0 && g_audio_padding == 0)) return;
            replace_stuck_thread_locked();
        }
        Sleep(2);
    }
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeClose(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_audio_mutex);
    close_audio_locked();
}

JNIEXPORT jstring JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeDeviceName(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> guard(g_audio_mutex);
    return env->NewString(
        reinterpret_cast<const jchar*>(g_audio_device_name.c_str()),
        static_cast<jsize>(g_audio_device_name.size()));
}

JNIEXPORT jint JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeStalls(JNIEnv*, jclass) {
    return g_audio_stalls.load();
}

JNIEXPORT jstring JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsAudio_nativeLastError(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> guard(g_audio_mutex);
    return env->NewString(
        reinterpret_cast<const jchar*>(g_audio_error.c_str()),
        static_cast<jsize>(g_audio_error.size()));
}

}  // extern "C"
