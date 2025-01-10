#!/system/bin/sh

mount -t overlay overlay -o lowerdir=/apex/com.android.btservices/lib64,upperdir=/data/adb/modules/btl2capfix/apex/com.android.btservices/lib64,workdir=/data/adb/modules/btl2capfix/apex/com.android.btservices/work /apex/com.android.btservices/lib64
mount -t overlay overlay -o lowerdir=/apex/com.android.btservices@352090000/lib64,upperdir=/data/adb/modules/btl2capfix/apex/com.android.btservices@352090000/lib64,workdir=/data/adb/modules/btl2capfix/apex/com.android.btservices@352090000/work /apex/com.android.btservices@352090000/lib64