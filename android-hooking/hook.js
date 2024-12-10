Java.perform(function () {
    // Locate the native library
    var libbluetooth = Module.findExportByName("libbluetooth_jni.so", "l2c_fcr_chk_chan_modes");
    
    if (libbluetooth) {
        console.log("Found l2c_fcr_chk_chan_modes at: " + libbluetooth);
        
        // Hook the function
        Interceptor.attach(libbluetooth, {
            onEnter: function (args) {
                console.log("l2c_fcr_chk_chan_modes called");
            },
            onLeave: function (retval) {
                console.log("Original return value: " + retval.toInt32());
                retval.replace(1); // Force return value to true (non-zero)
                console.log("Modified return value to: " + retval.toInt32());
            }
        });
    } else {
        console.log("l2c_fcr_chk_chan_modes not found!");
    }
});
