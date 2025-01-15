#!/bin/sh

set -eux

(cd root-module && rm -f btl2capfix.zip && zip -r btl2capfix.zip . -x \*.DS_Store \*__MACOSX \*DEBIAN -x btl2capfix.zip)
