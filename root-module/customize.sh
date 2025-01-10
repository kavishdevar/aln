#!/system/bin/sh

API_URL="https://aln.kavishdevar.me/api"
TEMP_DIR="$TMPDIR/aln_patch"
PATCHED_FILE_NAME=""
SOURCE_FILE=""
LIBRARY_NAME=""
mkdir -p "$TEMP_DIR"

log() {
    echo "[Bluetooth L2CAP Patch] $1"
}

if [ -f "/apex/com.android.btservices/lib64/libbluetooth_jni.so" ]; then
    SOURCE_FILE="/apex/com.android.btservices/lib64/libbluetooth_jni.so"
    LIBRARY_NAME="libbluetooth_jni.so"
    PATCHED_FILE_NAME="libbluetooth_jni_patched.so"
    log "Detected library: libbluetooth_jni.so in /apex/com.android.btservices/lib64/"
elif [ -f "/system/lib64/libbluetooth_jni.so" ]; then
    SOURCE_FILE="/system/lib64/libbluetooth_jni.so"
    LIBRARY_NAME="libbluetooth_jni.so"
    PATCHED_FILE_NAME="libbluetooth_jni_patched.so"
    log "Detected library: libbluetooth_jni.so in /system/lib64/"
elif [ -f "/system/lib64/libbluetooth_qti.so" ]; then
    SOURCE_FILE="/system/lib64/libbluetooth_qti.so"
    LIBRARY_NAME="libbluetooth_qti.so"
    PATCHED_FILE_NAME="libbluetooth_qti_patched.so"
    log "Detected QTI library: libbluetooth_qti.so in /system/lib64/"
elif [ -f "/system_ext/lib64/libbluetooth_qti.so" ]; then
    SOURCE_FILE="/system_ext/lib64/libbluetooth_qti.so"
    LIBRARY_NAME="libbluetooth_qti.so"
    PATCHED_FILE_NAME="libbluetooth_qti_patched.so"
    log "Detected QTI library: libbluetooth_qti.so in /system_ext/lib64/"
else
    log "No target library found. Exiting."
    exit 1
fi

log "Uploading $LIBRARY_NAME to the API for patching..."
log "If you're concerened about privacy, you can review the source code of the API at https://github.com/kavishdevar/aln/blob/main/root-module-manual/server.py"
PATCHED_FILE_NAME="patched_$LIBRARY_NAME"

curl -s -X POST "$API_URL" \
    -F "file=@$SOURCE_FILE" \
    -F "library_name=$LIBRARY_NAME" \
    -o "$TEMP_DIR/$PATCHED_FILE_NAME" \
    -D "$TEMP_DIR/headers.txt"

if [ -f "$TEMP_DIR/$PATCHED_FILE_NAME" ]; then
    log "Received patched file from the API."
    log "Installing patched file to the module's directory..."
    mkdir -p "$MODPATH/system/lib/"
    cp "$TEMP_DIR/$PATCHED_FILE_NAME" "$MODPATH/$LIBRARY_NAME"
    chmod 644 "$MODPATH/$LIBRARY_NAME"
    log "Patched file has been successfully installed at $MODPATH/system/lib/$PATCHED_FILE_NAME"
else
    ERROR_MESSAGE=$(grep -oP '(?<="error": ")[^"]+' "$TEMP_DIR/headers.txt")
    log "API Error: $ERROR_MESSAGE"
    rm -rf "$TEMP_DIR"
    exit 1
fi

rm -rf "$TEMP_DIR"
exit 0