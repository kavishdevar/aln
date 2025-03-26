#pragma once

#include <cstdint>
#include <vector>

typedef int (*HookFunType)(void *func, void *replace, void **backup);

typedef int (*UnhookFunType)(void *func);

typedef void (*NativeOnModuleLoaded)(const char *name, void *handle);

typedef struct {
    uint32_t version;
    HookFunType hook_func;
    UnhookFunType unhook_func;
} NativeAPIEntries;

[[maybe_unused]] typedef NativeOnModuleLoaded (*NativeInit)(const NativeAPIEntries *entries);

uintptr_t loadHookOffset(const char* package_name);
uintptr_t getModuleBase(const char *module_name);
