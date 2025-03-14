# AirPods Head Tracking Project

This project implements head tracking with AirPods by gathering sensor data over Bluetooth, processing orientation and acceleration values, and detecting head gestures. The codebase is split into the following components:

- **Connection and Data Collection**  
  The project uses a custom ConnectionManager (imported in multiple files) to connect via Bluetooth to AirPods. Once connected, sensor packets are received in raw hex format. An AirPodsTracker class (in `plot.py`) handles the start/stop of tracking, logging of raw data, and parsing of packets into useful fields.

- **Orientation Calculation and Visualization**  
  The `HeadOrientation` class (in `head_orientation.py`) is responsible for:
  - **Calibration:**  
    A set number of samples (default 10) are collected to calculate the neutral (baseline) values for the sensors. For example:  
    `o1_neutral = np.mean(samples[:, 0])`
  - **Calculating Angles:**  
    For each new packet, the raw orientation values are normalized by subtracting the neutral baseline. Then:
    - **Pitch** is computed as:
      ```
      pitch = (o2_norm + o3_norm) / 2 / 32000 * 180
      ```
      This averages the deviations from neutral, scales the result to degrees (assuming a sensor range around 32000), thus giving a smooth estimation of up/down tilt.
    - **Yaw** is computed as:
      ```
      yaw = (o2_norm - o3_norm) / 2 / 32000 * 180
      ```
      Here, the difference between the two sensor axes is used to detect left/right rotation.
  - **ASCII Visualization:**  
    Based on the calculated pitch and yaw, an ASCII art "face" is generated. The algorithm rotates points on a circle using simple trigonometric formulas (with scaling factors based on sensor depth) to build an approximate visual representation of head orientation.

- **Live Plotting and Interactive Commands**  
  The code offers both terminal-based plotting and graphical plotting via matplotlib. The AirPodsTracker manages live plotting by maintaining a buffer of recent packets. When in terminal mode, the code uses libraries like `asciichartpy` and `drawille` to render charts; in graphical mode, it creates live-updating plots.

- **Gesture Detection**  
  The `GestureDetector` class (in `gestures.py`) processes the head tracking data to detect nodding ("Yes") or head shaking ("No"):
  - **Smoothing:**  
    Raw horizontal and vertical sensor data undergo moving-average smoothing using small fixed-size buffers. This reduces noise and provides a steadier signal.
  - **Peak and Trough Detection:**  
    The code monitors small sections (e.g. the last 4 values) to compute variance and dynamically determine thresholds for direction changes. When a significant reversal (e.g. from increasing to decreasing) is detected that surpasses the dynamic threshold value (derived partly from a fixed threshold and variance), a peak or trough is recorded.
  - **Rhythm Consistency:**  
    Time intervals between detected peaks are captured. The consistency of these intervals (by comparing them to their mean and computing relative variance) is used to evaluate whether the movement is rhythmic—a trait of intentional gestures.
  - **Confidence Calculation:**  
    Multiple factors are considered:
    - **Amplitude Factor:** Compares the average detected peak amplitude with a constant (like 600) to provide a normalized measure.
    - **Rhythm Factor:** Derived from the consistency of the time intervals of the peaks.
    - **Alternation Factor:** Verifies that the signal alternates (for instance, switching between positive and negative values).
    - **Isolation Factor:** Checks that movement on the target axis (vertical for nodding, horizontal for shaking) dominates over the non-target axis.
  
    A weighted sum of these factors forms a confidence score which, if above a predefined threshold (e.g. 0.7), confirms a detected gesture.