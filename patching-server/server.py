from flask import Flask, request, jsonify, send_file
import os
import uuid
import logging
import re
import subprocess
import sys

app = Flask(__name__)

# Configure logging
class LogColors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

class ColoredFormatter(logging.Formatter):
    def format(self, record):
        log_colors = {
            'DEBUG': LogColors.OKCYAN,
            'INFO': LogColors.OKGREEN,
            'WARNING': LogColors.WARNING,
            'ERROR': LogColors.FAIL,
            'CRITICAL': LogColors.FAIL + LogColors.BOLD
        }
        log_color = log_colors.get(record.levelname, LogColors.ENDC)
        record.msg = f"{log_color}{record.msg}{LogColors.ENDC}"
        return super().format(record)

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger()
handler = logger.handlers[0]
handler.setFormatter(ColoredFormatter('%(asctime)s - %(levelname)s - %(message)s'))

def run_command(command):
    logging.info(f"Running command: {command}")
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    if result.returncode != 0 and "Cannot determine entrypoint" not in result.stderr:
        logging.error(f"Command failed: {command}\n{result.stderr}")
        sys.exit(1)
    logging.info(f"Command output: {result.stdout}")
    return result.stdout

def get_symbol_address(file_path, symbol_name):
    logging.info(f"Getting address for symbol: {symbol_name}")
    output = run_command(f"radare2 -q -e bin.cache=true -c 'is~{symbol_name}' -z {file_path}")
    match = re.search(r'0x[0-9a-fA-F]+', output)
    if match:
        address = match.group(0)
        logging.info(f"Found address for {symbol_name}: {address}")
        return address
    else:
        logging.error(f"Symbol {symbol_name} not found in {file_path}")
        sys.exit(1)

def patch_address(file_path, address, patch_bytes):
    logging.info(f"Patching address {address} with bytes: {patch_bytes}")
    run_command(f"radare2 -q -e bin.cache=true -w -c 's {address}; wx {patch_bytes}; wci' {file_path}")
    logging.info(f"Successfully patched address {address}")

@app.route('/api', methods=['POST'])
def api():
    if 'file' not in request.files:
        return jsonify({"error": "No file part"}), 400
    file = request.files['file']
    if file.filename == '':
        return jsonify({"error": "No selected file"}), 400
    if not file.filename.endswith('.so'):
        return jsonify({"error": "Invalid file type. Only .so files are allowed."}), 400

    file_uuid = str(uuid.uuid4())
    file_path = os.path.join('uploads', f"{file_uuid}_{file.filename}")
    file.save(file_path)

    try:
        l2c_fcr_chk_chan_modes_address = get_symbol_address(file_path, "l2c_fcr_chk_chan_modes")
        patch_address(file_path, l2c_fcr_chk_chan_modes_address, "20008052c0035fd6")
        l2cu_send_peer_info_req_address = get_symbol_address(file_path, "l2cu_send_peer_info_req")
        patch_address(file_path, l2cu_send_peer_info_req_address, "c0035fd6")
    except Exception as e:
        logger.error(f"Error patching file: {str(e)}")
        return jsonify({"error": f"Error patching file: {str(e)}"}), 500

    try:
        return send_file(file_path, as_attachment=True, download_name=file.filename)
    except Exception as e:
        logger.error(f"Error sending file: {str(e)}")
        return jsonify({"error": f"Error sending file: {str(e)}"}), 500

if not os.path.exists('uploads'):
    os.makedirs('uploads')
if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=8080)
