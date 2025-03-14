import bluetooth
import threading
import time
import logging
import statistics
from collections import deque

class Colors:
    RESET = "\033[0m"
    BOLD = "\033[1m"
    RED = "\033[91m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    BLUE = "\033[94m"
    MAGENTA = "\033[95m"
    CYAN = "\033[96m"
    WHITE = "\033[97m"
    BG_BLACK = "\033[40m"

class ColorFormatter(logging.Formatter):
    FORMATS = {
        logging.DEBUG: Colors.BLUE + "[%(levelname)s] %(message)s" + Colors.RESET,
        logging.INFO: Colors.GREEN + "%(message)s" + Colors.RESET,
        logging.WARNING: Colors.YELLOW + "%(message)s" + Colors.RESET,
        logging.ERROR: Colors.RED + "[%(levelname)s] %(message)s" + Colors.RESET,
        logging.CRITICAL: Colors.RED + Colors.BOLD + "[%(levelname)s] %(message)s" + Colors.RESET
    }

    def format(self, record):
        log_fmt = self.FORMATS.get(record.levelno)
        formatter = logging.Formatter(log_fmt, datefmt="%H:%M:%S")
        return formatter.format(record)

handler = logging.StreamHandler()
handler.setFormatter(ColorFormatter())
log = logging.getLogger(__name__)
log.setLevel(logging.INFO)
log.addHandler(handler)
log.propagate = False

class GestureDetector:
    INIT_CMD  = "00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00"
    START_CMD = "04 00 04 00 17 00 00 00 10 00 10 00 08 A1 02 42 0B 08 0E 10 02 1A 05 01 40 9C 00 00"
    STOP_CMD  = "04 00 04 00 17 00 00 00 10 00 11 00 08 7E 10 02 42 0B 08 4E 10 02 1A 05 01 00 00 00 00"

    def __init__(self, conn=None):
        self.sock = None
        self.bt_addr = "28:2D:7F:C2:05:5B"
        self.psm = 0x1001
        self.running = False
        self.data_lock = threading.Lock()
        
        self.horiz_buffer = deque(maxlen=100)
        self.vert_buffer = deque(maxlen=100)
        
        self.horiz_avg_buffer = deque(maxlen=5)
        self.vert_avg_buffer = deque(maxlen=5)
        
        self.horiz_peaks = []
        self.horiz_troughs = []
        self.vert_peaks = []
        self.vert_troughs = []
        
        self.last_peak_time = 0
        self.peak_intervals = deque(maxlen=5)
        
        self.peak_threshold = 400
        self.direction_change_threshold = 175
        self.rhythm_consistency_threshold = 0.5
        
        self.horiz_increasing = None
        self.vert_increasing = None
        
        self.required_extremes = 3
        self.detection_timeout = 15
        
        self.min_confidence_threshold = 0.7
        
        self.conn = conn

    def connect(self):
        try:
            log.info(f"Connecting to AirPods at {self.bt_addr}...")
            if self.conn is None:
                from connection_manager import ConnectionManager
                self.conn = ConnectionManager(self.bt_addr, self.psm, logger=log)
                if not self.conn.connect():
                    return False
            else:
                if not self.conn.connected:
                    if not self.conn.connect():
                        return False
            self.sock = self.conn.sock
            log.info(f"{Colors.GREEN}✓ Connected to AirPods via ConnectionManager{Colors.RESET}")
            return True
        except Exception as e:
            log.error(f"{Colors.RED}Connection failed: {e}{Colors.RESET}")
            return False
    
    def process_data(self):
        """Process incoming head tracking data."""
        self.conn.send_start()
        log.info(f"{Colors.GREEN}✓ Head tracking activated{Colors.RESET}")
    
        self.running = True
        start_time = time.time()

        log.info(f"{Colors.GREEN}Ready! Make a YES or NO gesture{Colors.RESET}")
        log.info(f"{Colors.YELLOW}Tip: Use natural, moderate speed head movements{Colors.RESET}")
        
        while self.running:
            if time.time() - start_time > self.detection_timeout:
                log.warning(f"{Colors.YELLOW}⚠️  Detection timeout reached. No gesture detected.{Colors.RESET}")
                self.running = False
                break
                
            try:
                if not self.sock:
                    log.error("Socket not available.")
                    break
                data = self.sock.recv(1024)
                formatted = self.format_hex(data)
                if self.is_valid_tracking_packet(formatted):
                    raw_bytes = bytes.fromhex(formatted.replace(" ", ""))
                    horizontal, vertical = self.extract_orientation_values(raw_bytes)
                    
                    if horizontal is not None and vertical is not None:
                        smooth_h, smooth_v = self.apply_smoothing(horizontal, vertical)
                        
                        with self.data_lock:
                            self.horiz_buffer.append(smooth_h)
                            self.vert_buffer.append(smooth_v)
                            
                            self.detect_peaks_and_troughs()
                            gesture = self.detect_gestures()
                            
                            if gesture:
                                self.running = False
                                break
        
            except Exception as e:
                if self.running:
                    log.error(f"Data processing error: {e}")
                break

    def disconnect(self):
        """Disconnect from socket."""
        self.conn.disconnect()

    def format_hex(self, data):
        """Format binary data to readable hex string."""
        hex_str = data.hex()
        return ' '.join(hex_str[i:i+2] for i in range(0, len(hex_str), 2))
    
    def is_valid_tracking_packet(self, hex_string):
        """Verify packet is a valid head tracking packet."""
        standard_header = "04 00 04 00 17 00 00 00 10 00 45 00"
        alternate_header = "04 00 04 00 17 00 00 00 10 00 44 00"
        if not hex_string.startswith(standard_header) and not hex_string.startswith(alternate_header):
            return False
            
        if len(hex_string.split()) < 80:
            return False
            
        return True
    
    def extract_orientation_values(self, raw_bytes):
        """Extract head orientation data from packet."""
        try:
            horizontal = int.from_bytes(raw_bytes[51:53], byteorder='little', signed=True)
            vertical = int.from_bytes(raw_bytes[53:55], byteorder='little', signed=True)
            
            return horizontal, vertical
        except Exception as e:
            log.debug(f"Failed to extract orientation: {e}")
            return None, None
    
    def apply_smoothing(self, horizontal, vertical):
        """Apply moving average smoothing (Apple-like filtering)."""
        self.horiz_avg_buffer.append(horizontal)
        self.vert_avg_buffer.append(vertical)
        
        smooth_horiz = sum(self.horiz_avg_buffer) / len(self.horiz_avg_buffer)
        smooth_vert = sum(self.vert_avg_buffer) / len(self.vert_avg_buffer)
        
        return smooth_horiz, smooth_vert
    
    def detect_peaks_and_troughs(self):
        """Detect motion direction changes with Apple-like refinements."""
        if len(self.horiz_buffer) < 4 or len(self.vert_buffer) < 4:
            return
            
        h_values = list(self.horiz_buffer)[-4:]
        v_values = list(self.vert_buffer)[-4:]
        
        h_variance = statistics.variance(h_values) if len(h_values) > 1 else 0
        v_variance = statistics.variance(v_values) if len(v_values) > 1 else 0
        
        current = self.horiz_buffer[-1]
        prev = self.horiz_buffer[-2]
        
        if self.horiz_increasing is None:
            self.horiz_increasing = current > prev
        
        dynamic_h_threshold = max(100, min(self.direction_change_threshold, h_variance / 3))
        
        if self.horiz_increasing and current < prev - dynamic_h_threshold:
            if abs(prev) > self.peak_threshold:
                self.horiz_peaks.append((len(self.horiz_buffer)-1, prev, time.time()))
                direction = "➡️ " if prev > 0 else "⬅️ "
                log.info(f"{Colors.CYAN}{direction} Horizontal max: {prev} (threshold: {dynamic_h_threshold:.1f}){Colors.RESET}")
                
                now = time.time()
                if self.last_peak_time > 0:
                    interval = now - self.last_peak_time
                    self.peak_intervals.append(interval)
                self.last_peak_time = now
                
            self.horiz_increasing = False
            
        elif not self.horiz_increasing and current > prev + dynamic_h_threshold:
            if abs(prev) > self.peak_threshold:
                self.horiz_troughs.append((len(self.horiz_buffer)-1, prev, time.time()))
                direction = "➡️ " if prev > 0 else "⬅️ "
                log.info(f"{Colors.CYAN}{direction} Horizontal max: {prev} (threshold: {dynamic_h_threshold:.1f}){Colors.RESET}")
                
                now = time.time()
                if self.last_peak_time > 0:
                    interval = now - self.last_peak_time
                    self.peak_intervals.append(interval)
                self.last_peak_time = now
                
            self.horiz_increasing = True
        
        current = self.vert_buffer[-1]
        prev = self.vert_buffer[-2]
        
        if self.vert_increasing is None:
            self.vert_increasing = current > prev
        
        dynamic_v_threshold = max(100, min(self.direction_change_threshold, v_variance / 3))
        
        if self.vert_increasing and current < prev - dynamic_v_threshold:
            if abs(prev) > self.peak_threshold:
                self.vert_peaks.append((len(self.vert_buffer)-1, prev, time.time()))
                direction = "⬆️ " if prev > 0 else "⬇️ "
                log.info(f"{Colors.MAGENTA}{direction} Vertical max: {prev} (threshold: {dynamic_v_threshold:.1f}){Colors.RESET}")
                
                now = time.time()
                if self.last_peak_time > 0:
                    interval = now - self.last_peak_time
                    self.peak_intervals.append(interval)
                self.last_peak_time = now
                
            self.vert_increasing = False
            
        elif not self.vert_increasing and current > prev + dynamic_v_threshold:
            if abs(prev) > self.peak_threshold:
                self.vert_troughs.append((len(self.vert_buffer)-1, prev, time.time()))
                direction = "⬆️ " if prev > 0 else "⬇️ "
                log.info(f"{Colors.MAGENTA}{direction} Vertical max: {prev} (threshold: {dynamic_v_threshold:.1f}){Colors.RESET}")
                
                now = time.time()
                if self.last_peak_time > 0:
                    interval = now - self.last_peak_time
                    self.peak_intervals.append(interval)
                self.last_peak_time = now
                
            self.vert_increasing = True
    
    def calculate_rhythm_consistency(self):
        """Calculate how consistent the timing between peaks is (Apple-like)."""
        if len(self.peak_intervals) < 2:
            return 0
            
        mean_interval = statistics.mean(self.peak_intervals)
        if mean_interval == 0:
            return 0
            
        variances = [(i/mean_interval - 1.0) ** 2 for i in self.peak_intervals]
        consistency = 1.0 - min(1.0, statistics.mean(variances) / self.rhythm_consistency_threshold)
        return max(0, consistency)
    
    def calculate_confidence_score(self, extremes, is_vertical=True):
        """Calculate confidence score for gesture detection (Apple-like)."""
        if len(extremes) < self.required_extremes:
            return 0.0
            
        sorted_extremes = sorted(extremes, key=lambda x: x[0])
        
        recent = sorted_extremes[-self.required_extremes:]
        
        avg_amplitude = sum(abs(val) for _, val, _ in recent) / len(recent)
        amplitude_factor = min(1.0, avg_amplitude / 600)
        
        rhythm_factor = self.calculate_rhythm_consistency()
        
        signs = [1 if val > 0 else -1 for _, val, _ in recent]
        alternating = all(signs[i] != signs[i-1] for i in range(1, len(signs)))
        alternation_factor = 1.0 if alternating else 0.5
        
        if is_vertical:
            vert_amp = sum(abs(val) for _, val, _ in recent) / len(recent)
            horiz_vals = list(self.horiz_buffer)[-len(recent)*2:]
            horiz_amp = sum(abs(val) for val in horiz_vals) / len(horiz_vals) if horiz_vals else 0
            isolation_factor = min(1.0, vert_amp / (horiz_amp + 0.1) * 1.2)
        else:
            horiz_amp = sum(abs(val) for _, val, _ in recent)
            vert_vals = list(self.vert_buffer)[-len(recent)*2:]
            vert_amp = sum(abs(val) for val in vert_vals) / len(vert_vals) if vert_vals else 0
            isolation_factor = min(1.0, horiz_amp / (vert_amp + 0.1) * 1.2)
        
        confidence = (
            amplitude_factor * 0.4 + 
            rhythm_factor * 0.2 +
            alternation_factor * 0.2 +
            isolation_factor * 0.2
        )
        
        return confidence
    
    def detect_gestures(self):
        """Recognize head gesture patterns with Apple-like intelligence."""
        if len(self.vert_peaks) + len(self.vert_troughs) >= self.required_extremes:
            all_extremes = sorted(self.vert_peaks + self.vert_troughs, key=lambda x: x[0])
            
            confidence = self.calculate_confidence_score(all_extremes, is_vertical=True)
            
            log.info(f"Vertical motion confidence: {confidence:.2f} (need {self.min_confidence_threshold:.2f})")
            
            if confidence >= self.min_confidence_threshold:
                log.info(f"{Colors.GREEN}🎯 \"Yes\" Gesture Detected (confidence: {confidence:.2f}){Colors.RESET}")
                return "YES"
        
        if len(self.horiz_peaks) + len(self.horiz_troughs) >= self.required_extremes:
            all_extremes = sorted(self.horiz_peaks + self.horiz_troughs, key=lambda x: x[0])
            
            confidence = self.calculate_confidence_score(all_extremes, is_vertical=False)
            
            log.info(f"Horizontal motion confidence: {confidence:.2f} (need {self.min_confidence_threshold:.2f})")
            
            if confidence >= self.min_confidence_threshold:
                log.info(f"{Colors.GREEN}🎯 \"No\" gesture detected (confidence: {confidence:.2f}){Colors.RESET}")
                return "NO"
        
        return None
    
    def start_detection(self):
        """Begin gesture detection process."""
        log.info(f"{Colors.BOLD}{Colors.WHITE}Starting gesture detection...{Colors.RESET}")
        
        if not self.connect():
            log.error(f"{Colors.RED}Failed to connect to AirPods.{Colors.RESET}")
            return
        
        data_thread = threading.Thread(target=self.process_data)
        data_thread.daemon = True
        data_thread.start()
        
        try:
            data_thread.join(timeout=self.detection_timeout + 2)
            if data_thread.is_alive():
                log.warning(f"{Colors.YELLOW}⚠️  Timeout reached. Stopping detection.{Colors.RESET}")
                self.running = False
        except KeyboardInterrupt:
            log.info(f"{Colors.YELLOW}Detection canceled by user.{Colors.RESET}")
            self.running = False
        if __name__ == "__main__":
            self.disconnect()
        log.info(f"{Colors.GREEN}Gesture detection complete.{Colors.RESET}")

if __name__ == "__main__":
    print(f"{Colors.BG_BLACK}{Colors.CYAN}╔════════════════════════════════════════╗{Colors.RESET}")
    print(f"{Colors.BG_BLACK}{Colors.CYAN}║     AirPods Head Gesture Detector      ║{Colors.RESET}")
    print(f"{Colors.BG_BLACK}{Colors.CYAN}╚════════════════════════════════════════╝{Colors.RESET}")
    print(f"\n{Colors.WHITE}This program detects head gestures using AirPods:{Colors.RESET}")
    print(f"{Colors.GREEN}• YES: {Colors.WHITE}nodding head up and down{Colors.RESET}")
    print(f"{Colors.RED}• NO: {Colors.WHITE}shaking head left and right{Colors.RESET}\n")
    
    detector = GestureDetector()
    detector.start_detection()