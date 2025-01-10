#!/system/bin/sh


# Define variables
API_URL="https://aln.kavishdevar.me/api"
TEMP_DIR="$TMPDIR/aln_patch"
PATCHED_FILE_NAME=""
SOURCE_FILE=""
LIBRARY_NAME=""  # To store the name of the library being patched

# Create temporary directory
mkdir -p "$TEMP_DIR"

# Function to log messages
log() {
    echo "[ALN Patch] $1"
}

# Identify the library type
if [ -f "/apex/com.android.btservices/lib64/libbluetooth_jni.so" ]; then
    SOURCE_FILE="/apex/com.android.btservices/lib64/libbluetooth_jni.so"
    LIBRARY_NAME="libbluetooth_jni.so"
    PATCHED_FILE_NAME="libbluetooth_jni_patched.so"
    log "Detected Qualcomm library: libbluetooth_jni.so in /apex/com.android.btservices/lib64/"
elif [ -f "/system/lib64/libbluetooth_jni.so" ]; then
    SOURCE_FILE="/system/lib64/libbluetooth_jni.so"
    LIBRARY_NAME="libbluetooth_jni.so"
    PATCHED_FILE_NAME="libbluetooth_jni_patched.so"
    log "Detected Qualcomm library: libbluetooth_jni.so in /system/lib64/"
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

# Upload the library to the API
log "Uploading $LIBRARY_NAME to the API for patching..."
RESPONSE=$(curl -s -X POST "$API_URL" \
    -F "file=@$SOURCE_FILE" \
    -F "qti=$( [ "$LIBRARY_NAME" = "libbluetooth_qti.so" ] && echo "1" || echo "0")")

# Check if the response is a file (patched .so)
if echo "$RESPONSE" | grep -q "Content-Disposition"; then
    # Extract the patched .so file name from Content-Disposition header
    PATCHED_FILE_NAME="patched_$LIBRARY_NAME"
    log "Received patched file from the API."

    # Save the patched .so file
    echo "$RESPONSE" > "$TEMP_DIR/$PATCHED_FILE_NAME"
    # Note: Depending on how the server sends the file, you might need to handle binary data appropriately.

    # Move the patched file to the module's directory
    log "Installing patched file to the module's directory..."
    mkdir -p "$MODPATH/system/lib/"
    cp "$TEMP_DIR/$PATCHED_FILE_NAME" "$MODPATH/system/lib/"

    # Set permissions
    chmod 644 "$MODPATH/system/lib/$PATCHED_FILE_NAME"

    log "Patched file has been successfully installed at $MODPATH/system/lib/$PATCHED_FILE_NAME"
else
    # Assume JSON response with error
    ERROR_MESSAGE=$(echo "$RESPONSE" | grep -oP '(?<="error": ")[^"]+')
    log "API Error: $ERROR_MESSAGE"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# Cleanup
rm -rf "$TEMP_DIR"

exit 0
