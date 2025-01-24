#!/system/bin/sh
mount -t overlay overlay -o lowerdir=/apex/com.android.btservices/lib64/,upperdir=/data/adb/modules/btl2capfix/apex/com.android.btservices/lib64,workdir=/data/adb/modules/btl2capfix/apex/com.android.btservices/work Zapex/com.android.btservices/lib64
