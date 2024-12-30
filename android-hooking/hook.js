// AirPods like Normal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
// 
// Copyright (C) 2024 Kavish Devar
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published
// by the Free Software Foundation, either version 3 of the License.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
// 
// You should have received a copy of the GNU Affero General Public License
//  along with this program. If not, see <https://www.gnu.org/licenses/>.

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
