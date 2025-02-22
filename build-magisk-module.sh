#!/bin/sh

set -eux

OUTPUT_ZIP=${1:-../btl2capfix.zip}

cd root-module
rm -f "$OUTPUT_ZIP"

# COPYFILE_DISABLE env is a macOS fix to avoid parasitic files in ZIPs: https://superuser.com/a/260264
export COPYFILE_DISABLE=1

zip -r "$OUTPUT_ZIP" . -x \*.DS_Store \*__MACOSX \*DEBIAN ._\* .gitignore
