// A disk cache in front of the HLSL compiler Skia's Direct3D backend uses.
//
// Skia compiles every shader it needs with D3DCompile, the first time it needs it, on the drawing
// thread — each compile tens of milliseconds, and a frame that met a few new kinds of draw at once
// hitched visibly. Skia keeps what it compiled only for the life of the process, so every launch
// paid again. Skiko looks D3DCompile up with GetProcAddress; this library points skiko's import
// of GetProcAddress at a hook that hands back a caching D3DCompile instead. A compile is keyed by
// everything that goes into it — source, entry point, target, flags, defines — and its bytecode
// kept on disk: the next time the same shader is asked for, in this run or any later one, it is
// read back in microseconds instead of compiled. Nothing about the shader changes, only when it is
// made.
//
// Exported for the app, through JNA:
//   int bitchord_shadercache_install(const wchar_t* skikoModule, const wchar_t* directory)
// returns 1 once the hook is in place, 0 when skiko's import could not be found.
//   long long bitchord_shadercache_hits(), bitchord_shadercache_misses()

#include <windows.h>
#include <cstdint>
#include <cstring>
#include <cwchar>
#include <string>

namespace {

struct ShaderMacro { const char* name; const char* definition; };

// The few ID3DBlob members used here, by their place in the vtable.
struct Blob;
struct BlobVtbl {
    void* queryInterface;
    ULONG(WINAPI* addRef)(Blob*);
    ULONG(WINAPI* release)(Blob*);
    void*(WINAPI* getBufferPointer)(Blob*);
    SIZE_T(WINAPI* getBufferSize)(Blob*);
};
struct Blob { BlobVtbl* vtbl; };

using D3DCompileFn = HRESULT(WINAPI*)(const void*, SIZE_T, const char*, const ShaderMacro*, void*, const char*,
                                      const char*, UINT, UINT, Blob**, Blob**);
using D3DCreateBlobFn = HRESULT(WINAPI*)(SIZE_T, Blob**);
using GetProcAddressFn = FARPROC(WINAPI*)(HMODULE, LPCSTR);

GetProcAddressFn realGetProcAddress = nullptr;
D3DCompileFn realCompile = nullptr;
D3DCreateBlobFn createBlob = nullptr;
std::wstring cacheDir;
volatile LONG64 hits = 0;
volatile LONG64 misses = 0;

uint64_t fnv(uint64_t h, const void* data, size_t size) {
    const auto* p = static_cast<const unsigned char*>(data);
    for (size_t i = 0; i < size; ++i) {
        h ^= p[i];
        h *= 1099511628211ULL;
    }
    return h;
}

uint64_t fnvString(uint64_t h, const char* s) {
    if (s) h = fnv(h, s, std::strlen(s));
    const char zero = 0;
    return fnv(h, &zero, 1);
}

std::wstring pathFor(uint64_t key) {
    wchar_t name[32];
    swprintf(name, 32, L"%016llx.dxbc", static_cast<unsigned long long>(key));
    return cacheDir + L"\\" + name;
}

bool readCached(const std::wstring& path, Blob** out) {
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return false;
    LARGE_INTEGER size{};
    bool ok = false;
    if (GetFileSizeEx(file, &size) && size.QuadPart > 0 && size.QuadPart < (64LL << 20) && createBlob) {
        Blob* blob = nullptr;
        if (SUCCEEDED(createBlob(static_cast<SIZE_T>(size.QuadPart), &blob)) && blob) {
            DWORD read = 0;
            if (ReadFile(file, blob->vtbl->getBufferPointer(blob), static_cast<DWORD>(size.QuadPart), &read, nullptr) &&
                read == static_cast<DWORD>(size.QuadPart)) {
                *out = blob;
                ok = true;
            } else {
                blob->vtbl->release(blob);
            }
        }
    }
    CloseHandle(file);
    return ok;
}

void writeCached(const std::wstring& path, Blob* blob) {
    // Written beside and renamed into place, so a half-written file is never read back.
    std::wstring partial = path + L".part";
    HANDLE file = CreateFileW(partial.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return;
    DWORD written = 0;
    const auto size = static_cast<DWORD>(blob->vtbl->getBufferSize(blob));
    const bool ok = WriteFile(file, blob->vtbl->getBufferPointer(blob), size, &written, nullptr) && written == size;
    CloseHandle(file);
    if (ok) {
        MoveFileExW(partial.c_str(), path.c_str(), MOVEFILE_REPLACE_EXISTING);
    } else {
        DeleteFileW(partial.c_str());
    }
}

HRESULT WINAPI cachingCompile(const void* src, SIZE_T srcSize, const char* sourceName, const ShaderMacro* defines,
                              void* include, const char* entry, const char* target, UINT flags1, UINT flags2,
                              Blob** code, Blob** errors) {
    // Only what the bytes alone decide can be cached: a custom include handler could pull in
    // anything, so a compile with one goes straight through.
    if (include != nullptr || code == nullptr || cacheDir.empty()) {
        return realCompile(src, srcSize, sourceName, defines, include, entry, target, flags1, flags2, code, errors);
    }
    uint64_t key = 1469598103934665603ULL;
    key = fnv(key, src, srcSize);
    key = fnvString(key, entry);
    key = fnvString(key, target);
    key = fnv(key, &flags1, sizeof flags1);
    key = fnv(key, &flags2, sizeof flags2);
    for (const ShaderMacro* m = defines; m && m->name; ++m) {
        key = fnvString(key, m->name);
        key = fnvString(key, m->definition);
    }
    const std::wstring path = pathFor(key);
    if (readCached(path, code)) {
        if (errors) *errors = nullptr;
        InterlockedIncrement64(&hits);
        return S_OK;
    }
    const HRESULT result = realCompile(src, srcSize, sourceName, defines, include, entry, target, flags1, flags2, code, errors);
    if (SUCCEEDED(result) && *code) writeCached(path, *code);
    InterlockedIncrement64(&misses);
    return result;
}

FARPROC WINAPI hookedGetProcAddress(HMODULE module, LPCSTR name) {
    FARPROC found = realGetProcAddress(module, name);
    // An ordinal is no name; only D3DCompile is taken over.
    if (found && reinterpret_cast<uintptr_t>(name) > 0xFFFF && std::strcmp(name, "D3DCompile") == 0) {
        realCompile = reinterpret_cast<D3DCompileFn>(found);
        if (!createBlob) createBlob = reinterpret_cast<D3DCreateBlobFn>(realGetProcAddress(module, "D3DCreateBlob"));
        if (createBlob) return reinterpret_cast<FARPROC>(&cachingCompile);
    }
    return found;
}

// Points the GetProcAddress entry of [module]'s import table at the hook.
bool patchImport(HMODULE module) {
    auto* base = reinterpret_cast<unsigned char*>(module);
    auto* dos = reinterpret_cast<IMAGE_DOS_HEADER*>(base);
    if (dos->e_magic != IMAGE_DOS_SIGNATURE) return false;
    auto* nt = reinterpret_cast<IMAGE_NT_HEADERS*>(base + dos->e_lfanew);
    const auto& dir = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
    if (dir.VirtualAddress == 0) return false;
    bool patched = false;
    for (auto* desc = reinterpret_cast<IMAGE_IMPORT_DESCRIPTOR*>(base + dir.VirtualAddress); desc->Name; ++desc) {
        const char* dll = reinterpret_cast<const char*>(base + desc->Name);
        if (_stricmp(dll, "KERNEL32.dll") != 0) continue;
        auto* names = reinterpret_cast<IMAGE_THUNK_DATA*>(base + (desc->OriginalFirstThunk ? desc->OriginalFirstThunk : desc->FirstThunk));
        auto* slots = reinterpret_cast<IMAGE_THUNK_DATA*>(base + desc->FirstThunk);
        for (; names->u1.AddressOfData; ++names, ++slots) {
            if (IMAGE_SNAP_BY_ORDINAL(names->u1.Ordinal)) continue;
            auto* byName = reinterpret_cast<IMAGE_IMPORT_BY_NAME*>(base + names->u1.AddressOfData);
            if (std::strcmp(reinterpret_cast<const char*>(byName->Name), "GetProcAddress") != 0) continue;
            DWORD old = 0;
            if (!VirtualProtect(&slots->u1.Function, sizeof(slots->u1.Function), PAGE_READWRITE, &old)) continue;
            if (!realGetProcAddress) realGetProcAddress = reinterpret_cast<GetProcAddressFn>(slots->u1.Function);
            slots->u1.Function = reinterpret_cast<ULONG_PTR>(&hookedGetProcAddress);
            VirtualProtect(&slots->u1.Function, sizeof(slots->u1.Function), old, &old);
            patched = true;
        }
    }
    return patched;
}

}  // namespace

extern "C" __declspec(dllexport) int bitchord_shadercache_install(const wchar_t* skikoModule, const wchar_t* directory) {
    if (!skikoModule || !directory) return 0;
    HMODULE skiko = GetModuleHandleW(skikoModule);
    if (!skiko) return 0;
    cacheDir = directory;
    CreateDirectoryW(cacheDir.c_str(), nullptr);
    return patchImport(skiko) ? 1 : 0;
}

extern "C" __declspec(dllexport) long long bitchord_shadercache_hits() { return hits; }
extern "C" __declspec(dllexport) long long bitchord_shadercache_misses() { return misses; }
