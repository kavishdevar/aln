#!/system/bin/sh

API_URL="https://aln.kavishdevar.me/api"
TEMP_DIR="$TMPDIR/aln_patch"
UNZIP_DIR="/data/local/tmp/aln_unzip"
PATCHED_FILE_NAME=""
SOURCE_FILE=""
LIBRARY_NAME=""
APEX_DIR=false

mkdir -p "$TEMP_DIR"
mkdir -p "$UNZIP_DIR"

# Manually extract the $ZIPFILE to a temporary directory
ui_print "Extracting $ZIPFILE to $UNZIP_DIR"
unzip -o "$ZIPFILE" -d "$UNZIP_DIR" > /dev/null 2>&1
if [ $? -ne 0 ]; then
    ui_print "Failed to unzip $ZIPFILE"
    abort "Failed to unzip $ZIPFILE"
fi

ui_print "Extracted module files to $UNZIP_DIR"

# Determine architecture
IS64BIT=false
if [ "$(uname -m)" = "aarch64" ]; then
    IS64BIT=true
fi

if [ "$IS64BIT" = true ]; then
    export LD_LIBRARY_PATH="$UNZIP_DIR/libcurl-android/libs/arm64-v8a"
    export PATH="$UNZIP_DIR/libcurl-android/bin/arm64-v8a:$PATH"
    export CURL_CMD="$UNZIP_DIR/libcurl-android/bin/arm64-v8a/curl"
    ln -s "$UNZIP_DIR/libcurl-android/libs/arm64-v8a/libz.so" "$UNZIP_DIR/libcurl-android/libs/arm64-v8a/libz.so.1"
else
    export LD_LIBRARY_PATH="$UNZIP_DIR/libcurl-android/libs/armeabi-v7a"
    export PATH="$UNZIP_DIR/libcurl-android/bin/armeabi-v7a:$PATH"
    export CURL_CMD="$UNZIP_DIR/libcurl-android/bin/armeabi-v7a/curl"
    ln -s "$UNZIP_DIR/libcurl-android/libs/armeabi-v7a/libz.so" "$UNZIP_DIR/libcurl-android/libs/armeabi-v7a/libz.so.1"
fi

set_perm "$CURL_CMD" 0 0 755

if [ -f "$CURL_CMD" ]; then
    ui_print "curl binary found."
else
    ui_print "curl binary not found. Exiting."
    abort "curl binary not found."
fi

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

ui_print "calling command $CURL_CMD --verbose -k -X POST $API_URL -F file=@$SOURCE_FILE -F library_name=$LIBRARY_NAME -o $TEMP_DIR/$PATCHED_FILE_NAME"

$CURL_CMD --verbose -k -X POST $API_URL -F file=@"$SOURCE_FILE" -F library_name="$LIBRARY_NAME" -o "$TEMP_DIR/$PATCHED_FILE_NAME" > "$TEMP_DIR/headers.txt" 2>&1

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
    ui_print "Failed to receive patched file from the API."
    rm -rf "$TEMP_DIR"
    abort "Failed to patch the library."
fi