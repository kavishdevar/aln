#!/system/bin/sh

# Note: these two exec redirs are not strictly POSIX-compliant, so they can be commented out if we notice that it shows a syntax error in some environments (unlikely to happen)

# Redirect stdout to ui_print otherwise it's not shown
exec 1> >(while read -r line; do ui_print "[O] $line"; done)
# Redirect stderr to ui_print otherwise it's not shown + ignore useless radare2 warning that clutters the logs
exec 2> >(while read -r line; do echo "$line" | grep -qv "Cannot determine entrypoint, using" && ui_print "[E] $line"; done)

TEMP_DIR="/data/local/tmp/aln_patch"

# Note: this dir cannot be changed without recompiling radare2 because this prefix are hardcoded inside the radare2 binaries: /data/local/tmp/aln_unzip/org.radare.radare2installer/radare2/
UNZIP_DIR="/data/local/tmp/aln_unzip"
SOURCE_FILE=""
LIBRARY_NAME=""
APEX_DIR=false

# Clean things up if the script crashes or exits
trap 'rm -rf "$TEMP_DIR" "$UNZIP_DIR"' EXIT INT TERM

# https://github.com/Magisk-Modules-Repo/busybox-ndk/blob/master/busybox-arm64
BUSYBOX="$UNZIP_DIR/busybox/busybox-arm64"
XZ="$UNZIP_DIR/busybox/xz"

rm -rf "$TEMP_DIR" "$UNZIP_DIR"
mkdir -p "$TEMP_DIR" "$UNZIP_DIR"

# Manually extract the $ZIPFILE to a temporary directory
ui_print "Extracting module files..."
unzip -d "$UNZIP_DIR" -oq "$ZIPFILE" || {
    ui_print "Error: Failed to extract module files."
    abort "Failed to unzip $ZIPFILE"
}

set_perm "$BUSYBOX" 0 0 755
set_perm "$XZ" 0 0 755

# The bundled radare2 is a custom build that works without Termux: https://github.com/devnoname120/radare2
ui_print "Extracting radare2 to /data/local/tmp/aln_unzip..."
$BUSYBOX tar xzf "$UNZIP_DIR/radare2-5.9.9-android-aarch64.tar.gz" -C / || {
    abort "Failed to extract "$UNZIP_DIR/radare2-5.9.9-android-aarch64.tar.gz"."
}


if [ "$(uname -m)" = "aarch64" ]; then
    export LD_LIBRARY_PATH="$UNZIP_DIR/org.radare.radare2installer/radare2/lib:$LD_LIBRARY_PATH"
    export PATH="$UNZIP_DIR/org.radare.radare2installer/radare2/bin:$PATH"
    export PATH="$UNZIP_DIR/busybox:$PATH"
    export RABIN2="$UNZIP_DIR/org.radare.radare2installer/radare2/bin/rabin2"
    export RADARE2="$UNZIP_DIR/org.radare.radare2installer/radare2/bin/radare2"
else
    abort "arm64 archicture required, arm32 not supported"
fi

set_perm "$RABIN2" 0 0 755
set_perm "$RADARE2" 0 0 755

if [ -f "$RABIN2" ]; then
    ui_print "rabin2 binary is ready."
else
    ui_print "Error: rabin2 binary not found."
    abort "rabin2 binary not found."
fi

if [ -f "$RADARE2" ]; then
    ui_print "radare2 binary is ready."
else
    ui_print "Error: radare2 binary not found."
    abort "radare2 binary not found."
fi

if [ -f "$BUSYBOX" ]; then
    ui_print "busybox binary is ready."
else
    ui_print "Error: busybox binary not found."
    abort "busybox binary not found."
fi

if [ -f "$XZ" ]; then
    ui_print "xz shim is ready."
else
    ui_print "Error: xz shim not found."
    abort "xz shim not found."
fi

for lib_path in \
    "/apex/com.android.btservices/lib64/libbluetooth_jni.so" \
    "/system/lib64/libbluetooth_jni.so" \
    "/system/lib64/libbluetooth_qti.so" \
    "/system_ext/lib64/libbluetooth_qti.so"; do
    if [ -f "$lib_path" ]; then
        ui_print "Detected library: $lib_path"
        [ -z "$SOURCE_FILE" ] && SOURCE_FILE="$lib_path"
        [ -z "$LIBRARY_NAME" ] && LIBRARY_NAME="$(basename "$lib_path")"
    fi
done

[ -z "$SOURCE_FILE" ] && {
    ui_print "Error: No target library found."
    abort "No target library found."
}

if echo "$LIBRARY_NAME" | grep -q "qti"; then
  ui_print "ERROR: \"qti\" Bluetooth libraries are NOT supported by the patcher and you won't be able to use aln. Aborting..."
  abort "Bluetooth driver not compatible."
fi

ui_print "Calculating patch addresses for $SOURCE_FILE..."

# export R2_LIBDIR="$UNZIP_DIR/radare2-android/libs/arm64-v8a"
# export R2_BINDIR="$UNZIP_DIR/radare2-android/bin/arm64-v8a"

# $RADARE2 -H 1>&2

# ldd $RABIN2 1>&2
# ldd $RADARE2 1>&2

symbols="$($RABIN2 -q -E "$SOURCE_FILE")" || abort "Failed to extract symbols from $SOURCE_FILE."

get_symbol_address() {
    symb_address=$(echo "$symbols" | grep "$1" | cut -d ' ' -f1 | tr -d '\n')
    [ -n "$symb_address" ] || abort "Failed to obtain address for symbol $1"
    echo "$symb_address"
}

l2c_fcr_chk_chan_modes_address="$(get_symbol_address 'l2c_fcr_chk_chan_modes')"
ui_print "  l2c_fcr_chk_chan_modes_address=$l2c_fcr_chk_chan_modes_address"

l2cu_send_peer_info_req_address="$(get_symbol_address 'l2cu_send_peer_info_req')"
ui_print "  l2cu_send_peer_info_req_address=$l2cu_send_peer_info_req_address"


cp "$SOURCE_FILE" "$TEMP_DIR"

ui_print "Patching $LIBRARY_NAME..."

apply_patch() {
    $RADARE2 -q -e bin.cache=true -w -c "s $1; wx $2; wci" "$TEMP_DIR/$LIBRARY_NAME" || abort "Failed to apply $1 patch."
}

apply_patch "$l2c_fcr_chk_chan_modes_address" "20008052c0035fd6"
apply_patch "$l2cu_send_peer_info_req_address" "c0035fd6"

if [ -f "$TEMP_DIR/$LIBRARY_NAME" ]; then
    ui_print "Installing patched file..."

    if echo "$SOURCE_FILE" | grep -q "/system/lib64"; then
        TARGET_DIR="$MODPATH/system/lib64"
    elif echo "$SOURCE_FILE" | grep -q "/apex/"; then
        TARGET_DIR="$MODPATH/system/lib64"
        APEX_DIR=true
    else
        TARGET_DIR="$MODPATH/system/lib"
    fi

    mkdir -p "$TARGET_DIR"

    cp "$TEMP_DIR/$LIBRARY_NAME" "$TARGET_DIR/$LIBRARY_NAME"
    set_perm "$TARGET_DIR/$LIBRARY_NAME" 0 0 644
    ui_print "Patched file installed at $TARGET_DIR/$LIBRARY_NAME"

    if [ "$APEX_DIR" = true ]; then
        POST_DATA_FS_SCRIPT="$MODPATH/post-data-fs.sh"
        APEX_LIB_DIR="/apex/com.android.btservices/lib64"
        MOD_APEX_LIB_DIR="$MODPATH/apex/com.android.btservices/lib64"
        WORK_DIR="$MODPATH/apex/com.android.btservices/work"

        mkdir -p "$MOD_APEX_LIB_DIR" "$WORK_DIR"

        cp "$TEMP_DIR/$LIBRARY_NAME" "$MOD_APEX_LIB_DIR/$LIBRARY_NAME"
        set_perm "$MOD_APEX_LIB_DIR/$LIBRARY_NAME" 0 0 644

        cat <<EOF > "$POST_DATA_FS_SCRIPT"
#!/system/bin/sh
mount -t overlay overlay -o lowerdir=$APEX_LIB_DIR,upperdir=$MOD_APEX_LIB_DIR,workdir=$WORK_DIR $APEX_LIB_DIR
EOF

        set_perm "$POST_DATA_FS_SCRIPT" 0 0 755
        ui_print "Created script for apex library handling."
        ui_print "You can now restart your device and test aln!"
        ui_print "Note: If your Bluetooth doesn't work anymore after restarting, then uninstall this module and report the issue at the link below."
        ui_print "https://github.com/kavishdevar/aln/issues/new"
    fi
else
    ui_print "Error: patched file missing."
    rm -rf "$TEMP_DIR" "$UNZIP_DIR"
    abort "Failed to patch the library."
fi

rm -rf "$TEMP_DIR" "$UNZIP_DIR"
