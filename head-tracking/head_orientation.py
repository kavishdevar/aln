import math
import drawille
import numpy as np
import logging
import os

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


class HeadOrientation:
    def __init__(self, use_terminal=False):
        self.orientation_offset = 5500
        self.o1_neutral = 19000
        self.o2_neutral = 0
        self.o3_neutral = 0
        self.calibration_samples = []
        self.calibration_complete = False
        self.calibration_sample_count = 10
        self.fig = None
        self.ax = None
        self.arrow = None
        self.animation = None
        self.use_terminal = use_terminal

    def reset_calibration(self):
        self.calibration_samples = []
        self.calibration_complete = False

    def add_calibration_sample(self, orientation_values):
        if len(self.calibration_samples) < self.calibration_sample_count:
            self.calibration_samples.append(orientation_values)
            return False
        if not self.calibration_complete:
            self._calculate_calibration()
            return True
        return True

    def _calculate_calibration(self):
        if len(self.calibration_samples) < 3:
            log.warning("Not enough calibration samples")
            return
        samples = np.array(self.calibration_samples)
        self.o1_neutral = np.mean(samples[:, 0])
        avg_o2 = np.mean(samples[:, 1])
        avg_o3 = np.mean(samples[:, 2])
        self.o2_neutral = avg_o2
        self.o3_neutral = avg_o3
        log.info("Calibration complete: o1_neutral=%.2f, o2_neutral=%.2f, o3_neutral=%.2f", 
                    self.o1_neutral, self.o2_neutral, self.o3_neutral)
        self.calibration_complete = True

    def calculate_orientation(self, o1, o2, o3):
        if not self.calibration_complete:
            return {'pitch': 0, 'yaw': 0}
        o1_norm = o1 - self.o1_neutral
        o2_norm = o2 - self.o2_neutral
        o3_norm = o3 - self.o3_neutral
        pitch = (o2_norm + o3_norm) / 2 / 32000 * 180
        yaw = (o2_norm - o3_norm) / 2 / 32000 * 180
        return {'pitch': pitch, 'yaw': yaw}

    def create_face_art(self, pitch, yaw):
        if self.use_terminal:
            try:
                ts = os.get_terminal_size()
                width, height = ts.columns, ts.lines * 2
            except Exception:
                width, height = 80, 40
        else:
            width, height = 80, 40
        center_x, center_y = width // 2, height // 2
        radius = (min(width, height) // 2 - 2) // 2
        pitch_rad = math.radians(pitch)
        yaw_rad = math.radians(yaw)
        canvas = drawille.Canvas()
        def rotate_point(x, y, z, pitch_r, yaw_r):
            cos_y, sin_y = math.cos(yaw_r), math.sin(yaw_r)
            cos_p, sin_p = math.cos(pitch_r), math.sin(pitch_r)
            x1 = x * cos_y - z * sin_y
            z1 = x * sin_y + z * cos_y
            y1 = y * cos_p - z1 * sin_p
            z2 = y * sin_p + z1 * cos_p
            scale = 1 + (z2 / width)
            return int(center_x + x1 * scale), int(center_y + y1 * scale)
        for angle in range(0, 360, 2):
            rad = math.radians(angle)
            x = radius * math.cos(rad)
            y = radius * math.sin(rad)
            x1, y1 = rotate_point(x, y, 0, pitch_rad, yaw_rad)
            canvas.set(x1, y1)
        for eye in [(-radius//2, -radius//3, 2), (radius//2, -radius//3, 2)]:
            ex, ey, ez = eye
            x1, y1 = rotate_point(ex, ey, ez, pitch_rad, yaw_rad)
            for dx in [-1, 0, 1]:
                for dy in [-1, 0, 1]:
                    canvas.set(x1 + dx, y1 + dy)
        nx, ny = rotate_point(0, 0, 1, pitch_rad, yaw_rad)
        for dx in [-1, 0, 1]:
            for dy in [-1, 0, 1]:
                canvas.set(nx + dx, ny + dy)
        smile_depth = radius // 8
        mouth_local_y = radius // 4
        mouth_length = radius
        for x_offset in range(-mouth_length // 2, mouth_length // 2 + 1):
            norm = abs(x_offset) / (mouth_length / 2)
            y_offset = int((1 - norm ** 2) * smile_depth)
            local_x = x_offset
            local_y = mouth_local_y + y_offset
            mx, my = rotate_point(local_x, local_y, 0, pitch_rad, yaw_rad)
            canvas.set(mx, my)
        return canvas.frame()
