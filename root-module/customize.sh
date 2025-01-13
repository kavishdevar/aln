#!/system/bin/sh

API_URL="https://aln.kavishdevar.me/api"
TEMP_DIR="$TMPDIR/aln_patch"
PATCHED_FILE_NAME=""
SOURCE_FILE=""
LIBRARY_NAME=""
APEX_DIR=false

mkdir -p "$TEMP_DIR"

CURL_CMD=$(command -v curl || echo "$MODPATH/system/bin/curl")
export LD_LIBRARY_PATH="$MODPATH/system/lib64:$LD_LIBRARY_PATH"

if [ -f "/apex/com.android.btservices/lib64/libbluetooth_jni.so" ]; then
    SOURCE_FILE="/apex/com.android.btservices/lib64/libbluetooth_jni.so"
    LIBRARY_NAME="libbluetooth_jni.so"
    PATCHED_FILE_NAME="libbluetooth_jni_patched.so"
    ui_print "Detected library: libbluetooth_jni.so in /apex/com.android.btservices/lib64/"
elif [ -f "/system/lib64/libbluetooth_jni.so" ]; then
    SOURCE_FILE="/system/lib64/libbluetooth_jni.so"
    LIBRARY_NAME="libbluetooth_jni.so"
    PATCHED_FILE_NAME="libbluetooth_jni_patched.so"
    ui_print "Detected library: libbluetooth_jni.so in /system/lib64/"
elif [ -f "/system/lib64/libbluetooth_qti.so" ]; then
    SOURCE_FILE="/system/lib64/libbluetooth_qti.so"
    LIBRARY_NAME="libbluetooth_qti.so"
    PATCHED_FILE_NAME="libbluetooth_qti_patched.so"
    ui_print "Detected QTI library: libbluetooth_qti.so in /system/lib64/"
elif [ -f "/system_ext/lib64/libbluetooth_qti.so" ]; then
    SOURCE_FILE="/system_ext/lib64/libbluetooth_qti.so"
    LIBRARY_NAME="libbluetooth_qti.so"
    PATCHED_FILE_NAME="libbluetooth_qti_patched.so"
    ui_print "Detected QTI library: libbluetooth_qti.so in /system_ext/lib64/"
else
    ui_print "No target library found. Exiting."
    abort "No target library found."
fi

ui_print "Uploading $LIBRARY_NAME to the API for patching..."
ui_print "If you're concerned about privacy, review the source code of the API at https://github.com/kavishdevar/aln/blob/main/root-module-manual/server.py"
PATCHED_FILE_NAME="patched_$LIBRARY_NAME"

$CURL_CMD -s -X POST "$API_URL" \
    -F "file=@$SOURCE_FILE" \
    -F "library_name=$LIBRARY_NAME" \
    -o "$TEMP_DIR/$PATCHED_FILE_NAME" \
    -D "$TEMP_DIR/headers.txt"

if [ -f "$TEMP_DIR/$PATCHED_FILE_NAME" ]; then
    ui_print "Received patched file from the API."
    ui_print "Installing patched file to the module's directory..."

    if [[ "$SOURCE_FILE" == *"/system/lib64"* ]]; then
        TARGET_DIR="$MODPATH/system/lib64"
    elif [[ "$SOURCE_FILE" == *"/apex/"* ]]; then
        TARGET_DIR="$MODPATH/system/lib64"
        APEX_DIR=true
    else
        TARGET_DIR="$MODPATH/system/lib"
    fi

    mkdir -p "$TARGET_DIR"

    cp "$TEMP_DIR/$PATCHED_FILE_NAME" "$TARGET_DIR/$LIBRARY_NAME"
    set_perm "$TARGET_DIR/$LIBRARY_NAME" 0 0 644
    ui_print "Patched file installed at $TARGET_DIR/$LIBRARY_NAME"

    if [ "$APEX_DIR" = true ]; then
        POST_DATA_FS_SCRIPT="$MODPATH/post-data-fs.sh"
        APEX_LIB_DIR="/apex/com.android.btservices/lib64"
        MOD_APEX_LIB_DIR="$MODPATH/apex/com.android.btservices/lib64"
        WORK_DIR="$MODPATH/apex/com.android.btservices/work"

        mkdir -p "$MOD_APEX_LIB_DIR"
        mkdir -p "$WORK_DIR"

        cp "$TEMP_DIR/$PATCHED_FILE_NAME" "$MOD_APEX_LIB_DIR/$LIBRARY_NAME"
        set_perm "$MOD_APEX_LIB_DIR/$LIBRARY_NAME" 0 0 644

        cat <<EOF > "$POST_DATA_FS_SCRIPT"
#!/system/bin/sh
mount -t overlay overlay -o lowerdir=$APEX_LIB_DIR,upperdir=$MOD_APEX_LIB_DIR,workdir=$WORK_DIR $APEX_LIB_DIR
EOF

        set_perm "$POST_DATA_FS_SCRIPT" 0 0 755
        ui_print "Created post-data-fs.sh script for apex library handling."
    fi
else
    ERROR_MESSAGE=$(grep -oP '(?<="error": ")[^"]+' "$TEMP_DIR/headers.txt")
    ui_print "API Error: $ERROR_MESSAGE"
    rm -rf "$TEMP_DIR"
    abort "Failed to patch the library."
fi

rm -rf "$TEMP_DIR"