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

// Define all necessary structures for the L2CAP stack

// Define base FCR structure
typedef struct {
    uint8_t mode;
    uint8_t tx_win_sz;
    uint8_t max_transmit;
    uint16_t rtrans_tout;
    uint16_t mon_tout;
    uint16_t mps;
} tL2CAP_FCR;

// Flow spec structure
typedef struct {
    uint8_t  qos_present;
    uint8_t  flow_direction;
    uint8_t  service_type;
    uint32_t token_rate;
    uint32_t token_bucket_size;
    uint32_t peak_bandwidth;
    uint32_t latency;
    uint32_t delay_variation;
} FLOW_SPEC;

// Configuration info structure
typedef struct {
    uint16_t result;
    uint16_t mtu_present;
    uint16_t mtu;
    uint16_t flush_to_present;
    uint16_t flush_to;
    uint16_t qos_present;
    FLOW_SPEC qos;
    uint16_t fcr_present;
    tL2CAP_FCR fcr;
    uint16_t fcs_present;
    uint16_t fcs;
    uint16_t ext_flow_spec_present;
    FLOW_SPEC ext_flow_spec;
} tL2CAP_CFG_INFO;

// Basic L2CAP link control block
typedef struct {
    bool wait_ack;
    // Other FCR fields - not needed for our specific hook
} tL2C_FCRB;

// Forward declarations for needed types
struct t_l2c_rcb;
struct t_l2c_lcb;

typedef struct t_l2c_ccb {
    struct t_l2c_ccb* p_next_ccb;  // Next CCB in the chain
    struct t_l2c_ccb* p_prev_ccb;  // Previous CCB in the chain
    struct t_l2c_lcb* p_lcb;       // Link this CCB belongs to
    struct t_l2c_rcb* p_rcb;       // Registration CB for this Channel

    uint16_t local_cid;            // Local CID
    uint16_t remote_cid;           // Remote CID
    uint16_t p_lcb_next;           // For linking CCBs to an LCB

    uint8_t ccb_priority;          // Channel priority
    uint16_t tx_mps;               // MPS for outgoing messages
    uint16_t max_rx_mtu;           // Max MTU we will receive

    // State variables
    bool in_use;                   // True when channel active
    uint8_t chnl_state;            // Channel state
    uint8_t local_id;              // Transaction ID for local trans
    uint8_t remote_id;             // Transaction ID for remote

    uint8_t timer_entry;           // Timer entry
    uint8_t is_flushable;          // True if flushable

    // Configuration variables
    uint16_t our_cfg_bits;         // Bitmap of local config bits
    uint16_t peer_cfg_bits;        // Bitmap of peer config bits
    uint16_t config_done;          // Configuration bitmask
    uint16_t remote_config_rsp_result; // Remote config response result

    tL2CAP_CFG_INFO our_cfg;       // Our saved configuration options
    tL2CAP_CFG_INFO peer_cfg;      // Peer's saved configuration options

    // Additional control fields
    uint8_t remote_credit_count;   // Credits sent to peer
    tL2C_FCRB fcrb;                // FCR info
    bool ecoc;                     // Enhanced Credit-based mode
} tL2C_CCB;

uint8_t fake_l2c_fcr_chk_chan_modes(void* p_ccb) {
    LOGI("l2c_fcr_chk_chan_modes hooked");

    auto* ccb = static_cast<tL2C_CCB*>(p_ccb);

    LOGI("Original FCR mode: 0x%02x", ccb->our_cfg.fcr.mode);

    ccb->our_cfg.fcr.mode = 0;

    ccb->our_cfg.fcr_present = true;

    ccb->peer_cfg.fcr.mode = 0;
    ccb->peer_cfg.fcr_present = true;

    LOGI("FCR mode set to Basic Mode (0) for both local and peer config, here's the new desired FCR mode: 0x%02x, and the peer's FCR mode: 0x%02x", ccb->our_cfg.fcr.mode, ccb->peer_cfg.fcr.mode);

    return 1;
}

uintptr_t loadHookOffset([[maybe_unused]] const char* package_name) {
    const char* property_name = "persist.aln.hook_offset";
    char value[PROP_VALUE_MAX] = {0};

    int len = __system_property_get(property_name, value);
    if (len > 0) {
        LOGI("Read hook offset from property: %s", value);

        uintptr_t offset;
        char* endptr = nullptr;

        const char* parse_start = value;
        if (value[0] == '0' && (value[1] == 'x' || value[1] == 'X')) {
            parse_start = value + 2;
        }

        errno = 0;
        offset = strtoul(parse_start, &endptr, 16);

        if (errno == 0 && endptr != parse_start && *endptr == '\0' && offset > 0) {
            LOGI("Parsed offset: 0x%x", offset);
            return offset;
        }

        LOGE("Failed to parse offset from property value: %s", value);
    }

    LOGI("Using hardcoded fallback offset");
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

