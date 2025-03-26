/*
 * AirPods like Normal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
 *
 * Copyright (C) 2024 Kavish Devar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

#include <cstdint>
#include <cstring>
#include <dlfcn.h>
#include <android/log.h>
#include <fstream>
#include <string>
#include <sys/system_properties.h>
#include "l2c_fcr_hook.h"

#define LOG_TAG "AirPodsHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static HookFunType hook_func = nullptr;

static uint8_t (*original_l2c_fcr_chk_chan_modes)(void* p_ccb) = nullptr;

uint8_t fake_l2c_fcr_chk_chan_modes([[maybe_unused]] void* p_ccb) {
    LOGI("l2c_fcr_chk_chan_modes hooked! Always returning true");
    return 1;
}

uintptr_t loadHookOffset([[maybe_unused]] const char* package_name) {
    const char* property_name = "persist.aln.hook_offset";
    char value[PROP_VALUE_MAX] = {0};

    int len = __system_property_get(property_name, value);
    if (len > 0) {
        LOGI("Read hook offset from property: %s", value);

        uintptr_t offset = 0;
        if (value[0] == '0' && (value[1] == 'x' || value[1] == 'X')) {
            sscanf(value + 2, "%x", &offset);
        } else {
            sscanf(value, "%x", &offset);
        }

        if (offset > 0) {
            LOGI("Parsed offset: 0x%x", offset);
            return offset;
        }
    }

    LOGI("Failed to read offset from property, using hardcoded fallback");
    return 0x00a55e30;
}

uintptr_t getModuleBase(const char *module_name) {
    FILE *fp;
    char line[1024];
    uintptr_t base_addr = 0;

    fp = fopen("/proc/self/maps", "r");
    if (!fp) {
        LOGE("Failed to open /proc/self/maps");
        return 0;
    }

    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, module_name)) {
            char *start_addr_str = line;
            char *end_addr_str = strchr(line, '-');
            if (end_addr_str) {
                *end_addr_str = '\0';
                base_addr = strtoull(start_addr_str, nullptr, 16);
                break;
            }
        }
    }

    fclose(fp);
    return base_addr;
}

bool findAndHookFunction([[maybe_unused]] const char *library_path) {
    if (!hook_func) {
        LOGE("Hook function not initialized");
        return false;
    }

    uintptr_t base_addr = getModuleBase("libbluetooth_jni.so");
    if (!base_addr) {
        LOGE("Failed to get base address of libbluetooth_jni.so");
        return false;
    }

    uintptr_t offset = loadHookOffset(nullptr);

    void* target = reinterpret_cast<void*>(base_addr + offset);
    LOGI("Using offset: 0x%x, base: %p, target: %p", offset, (void*)base_addr, target);

    int result = hook_func(target, (void*)fake_l2c_fcr_chk_chan_modes, (void**)&original_l2c_fcr_chk_chan_modes);

    if (result == 0) {
        LOGI("Successfully hooked l2c_fcr_chk_chan_modes");
        return true;
    } else {
        LOGE("Failed to hook function, error: %d", result);
        return false;
    }
}

void on_library_loaded(const char *name, [[maybe_unused]] void *handle) {
    if (strstr(name, "libbluetooth_jni.so")) {
        LOGI("Detected Bluetooth library: %s", name);

        bool hooked = findAndHookFunction(name);
        if (!hooked) {
            LOGE("Failed to hook Bluetooth library function");
        }
    }
}

extern "C" [[gnu::visibility("default")]] [[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries* entries) {
    LOGI("L2C FCR Hook module initialized");

    hook_func = entries->hook_func;

    return on_library_loaded;
}

