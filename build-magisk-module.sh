#!/bin/sh

set -eux

cd root-module
rm -f ../btl2capfix.zip

# COPYFILE_DISABLE env is a macOS fix to avoid parasitic files in ZIPs: https://superuser.com/a/260264
export COPYFILE_DISABLE=1

zip -r ../btl2capfix.zip . -x \*.DS_Store \*__MACOSX \*DEBIAN ._\* .gitignore
