#!/system/bin/sh

TEMP_DIR="$TMPDIR/aln_patch"
UNZIP_DIR="/data/local/tmp/aln_unzip"
SOURCE_FILE=""
LIBRARY_NAME=""
APEX_DIR=false

mkdir -p "$TEMP_DIR"
mkdir -p "$UNZIP_DIR"

# Manually extract the $ZIPFILE to a temporary directory
ui_print "Extracting module files..."
unzip -o "$ZIPFILE" -d "$UNZIP_DIR" > /dev/null 2>&1
if [ $? -ne 0 ]; then
    ui_print "Error: Failed to extract module files."
    abort "Failed to unzip $ZIPFILE"
fi

# Determine architecture
IS64BIT=false
if [ "$(uname -m)" = "aarch64" ]; then
    IS64BIT=true
fi

if [ "$IS64BIT" = true ]; then
    export LD_LIBRARY_PATH="$UNZIP_DIR/radare2-android/libs/arm64-v8a"
    export PATH="$UNZIP_DIR/radare2-android/bin/arm64-v8a:$PATH"
    export RABIN2="$UNZIP_DIR/radare2-android/bin/arm64-v8a/rabin2"
    export RADARE2="$UNZIP_DIR/radare2-android/bin/arm64-v8a/radare2"
else
    export LD_LIBRARY_PATH="$UNZIP_DIR/radare2-android/libs/armeabi-v7a"
    export PATH="$UNZIP_DIR/radare2-android/bin/armeabi-v7a:$PATH"
    export RABIN2="$UNZIP_DIR/radare2-android/bin/armeabi-v7a/rabin2"
    export RADARE2="$UNZIP_DIR/radare2-android/bin/armeabi-v7a/radare2"
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

if [ -f "/apex/com.android.btservices/lib64/libbluetooth_jni.so" ]; then
    SOURCE_FILE="/apex/com.android.btservices/lib64/libbluetooth_jni.so"
    LIBRARY_NAME="libbluetooth_jni.so"
    ui_print "Detected library: libbluetooth_jni.so"
elif [ -f "/system/lib64/libbluetooth_jni.so" ]; then
    SOURCE_FILE="/system/lib64/libbluetooth_jni.so"
    LIBRARY_NAME="libbluetooth_jni.so"
    ui_print "Detected library: libbluetooth_jni.so"
elif [ -f "/system/lib64/libbluetooth_qti.so" ]; then
    SOURCE_FILE="/system/lib64/libbluetooth_qti.so"
    LIBRARY_NAME="libbluetooth_qti.so"
    ui_print "Detected QTI library: libbluetooth_qti.so"
elif [ -f "/system_ext/lib64/libbluetooth_qti.so" ]; then
    SOURCE_FILE="/system_ext/lib64/libbluetooth_qti.so"
    LIBRARY_NAME="libbluetooth_qti.so"
    ui_print "Detected QTI library: libbluetooth_qti.so"
else
    ui_print "Error: No target library found."
    abort "No target library found."
fi

ui_print "Calculating patch addresses for $LIBRARY_NAME..."

exec 2> >(while read -r line; do ui_print "[E] $line"; done)

export l2c_fcr_chk_chan_modes_address="$($RABIN2 -q -E \"$SOURCE_FILE\" | grep l2c_fcr_chk_chan_modes | cut -d ' ' -f1 | tr -d \"\n\")"
export l2cu_send_peer_info_req_address="$($RABIN2 -q -E \"$SOURCE_FILE\" | grep l2cu_send_peer_info_req | cut -d ' ' -f1 | tr -d \"\n\")"

ui_print "Found l2c_fcr_chk_chan_modes_address=$l2c_fcr_chk_chan_modes_address"
ui_print "Found l2cu_send_peer_info_req_address=$l2cu_send_peer_info_req_address"

cp "$SOURCE_FILE" "$TEMP_DIR"

ui_print "Patching $LIBRARY_NAME..."

$RADARE2 -q -w -c "s $l2c_fcr_chk_chan_modes_address; wx 20008052c0035fd6; wci" "$TEMP_DIR/$LIBRARY_NAME"
$RADARE2 -q -w -c "s $l2cu_send_peer_info_req_address; wx c0035fd6; wci" "$TEMP_DIR/$LIBRARY_NAME"

if [ -f "$TEMP_DIR/$LIBRARY_NAME" ]; then
    ui_print "Installing patched file..."

    if [[ "$SOURCE_FILE" == *"/system/lib64"* ]]; then
        TARGET_DIR="$MODPATH/system/lib64"
    elif [[ "$SOURCE_FILE" == *"/apex/"* ]]; then
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

        mkdir -p "$MOD_APEX_LIB_DIR"
        mkdir -p "$WORK_DIR"

        cp "$TEMP_DIR/$LIBRARY_NAME" "$MOD_APEX_LIB_DIR/$LIBRARY_NAME"
        set_perm "$MOD_APEX_LIB_DIR/$LIBRARY_NAME" 0 0 644

        cat <<EOF > "$POST_DATA_FS_SCRIPT"
#!/system/bin/sh
mount -t overlay overlay -o lowerdir=$APEX_LIB_DIR,upperdir=$MOD_APEX_LIB_DIR,workdir=$WORK_DIR $APEX_LIB_DIR
EOF

        set_perm "$POST_DATA_FS_SCRIPT" 0 0 755
        ui_print "Created script for apex library handling."
    fi
else
    ui_print "Error: patched file missing."
    rm -rf "$TEMP_DIR"
    abort "Failed to patch the library."
fi

# rm -rf "$TEMP_DIR"
# rm -rf "$UNZIP_DIR"
# rm -rf "$MODPATH/radare2-android"
