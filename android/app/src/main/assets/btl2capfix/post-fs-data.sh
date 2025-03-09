#!/system/bin/sh
# Magisk Module Post-fs-data Script

# Define module directory
MODULE_DIR="/data/adb/modules/btl2capfix"

# Check if me.kavishdevar.aln package is installed
if pm list packages | grep -q "me.kavishdevar.aln"; then
    # Package found, keep the module
    echo "me.kavishdevar.aln detected - keeping btl2capfix module"
else
    # Package not found, remove the module
    echo "me.kavishdevar.aln not found - removing btl2capfix module"
    rm -rf "$MODULE_DIR"
fi
