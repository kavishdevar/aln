from flask import Flask, request, jsonify, send_file, make_response
import os
import json
import uuid
import time
import threading
import logging
from main import get_symbol_address, patch_address, copy_file_to_src, zip_src_files

app = Flask(__name__)
PATCHED_LIBRARIES = {}
PERMALINK_EXPIRY = 600  # 10 minutes
PATCHES_JSON = 'patches.json'

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

def save_patch_info(permalink_id, file_path):
    patch_info = {
        'permalink_id': permalink_id,
        'file_path': file_path,
        'timestamp': time.time()
    }
    if os.path.exists(PATCHES_JSON):
        with open(PATCHES_JSON, 'r') as f:
            patches = json.load(f)
    else:
        patches = []

    patches.append(patch_info)

    with open(PATCHES_JSON, 'w') as f:
        json.dump(patches, f, indent=4)

@app.route('/')
def index():
    return '''
    <!doctype html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Library Patcher</title>
        <style>
            body {
                background-color: #121212;
                color: #ffffff;
                font-family: Arial, sans-serif;
                display: flex;
                justify-content: center;
                align-items: center;
                height: 100vh;
                margin: 0;
                padding: 5vmax;
                box-sizing: border-box;
            }
            .container {
                text-align: center;
                background-color: #1e1e1e;
                padding: 20px;
                border-radius: 10px;
                box-shadow: 0 0 10px rgba(0, 0, 0, 0.5);
                width: 80%;
                max-width: 500px;
            }
            .file-upload {
                width: 100%;
                height: 150px;
                margin: 10px 0;
                padding: 10px;
                border-radius: 5px;
                border: 2px dashed #555;
                background-color: #333;
                color: #fff;
                box-sizing: border-box;
                display: flex;
                justify-content: center;
                align-items: center;
                cursor: pointer;
                position: relative;
            }
            .file-upload input[type="file"] {
                width: 100%;
                height: 100%;
                opacity: 0;
                position: absolute;
                cursor: pointer;
            }
            .file-upload-text {
                font-size: 16px;
                pointer-events: none;
            }
            .file-upload-text span {
                display: block;
                margin-top: 10px;
                font-size: 14px;
                color: #bbb;
            }
            input[type="submit"] {
                width: 100%;
                padding: 15px 20px;
                border-radius: 5px;
                border: none;
                background-color: #6200ea;
                color: #fff;
                cursor: pointer;
                transition: background-color 0.3s;
                box-sizing: border-box;
                display: block;
                font-size: 16px;
            }
            input[type="submit"]:hover {
                background-color: #3700b3;
            }
            .progress {
                display: none;
                margin-top: 20px;
                width: 100%;
                background-color: #333;
                border-radius: 5px;
                overflow: hidden;
            }
            .progress-bar {
                width: 0;
                height: 20px;
                background-color: #6200ea;
                transition: width 0.3s;
            }
            .progress-text {
                margin-top: 10px;
                font-size: 14px;
                color: #bbb;
            }
            .download-link {
                margin-top: 20px;
                display: none;
            }
            .download-link a {
                color: #6200ea;
                text-decoration: none;
                font-weight: bold;
            }
            .download-link a:hover {
                color: #3700b3;
            }
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Upload a library to patch</h1>
            <form id="upload-form" enctype="multipart/form-data">
                <div class="file-upload">
                    <input type="file" name="file" onchange="updateFileName(this)">
                    <span class="file-upload-text">Click to upload a file<span id="file-name"></span></span>
                </div>
                <label>
                    <input type="checkbox" name="qti" id="qti-checkbox"> Qualcomm library (qti)
                </label>
                <input type="submit" value="Patch" id="patch-button">
            </form>
            <div class="progress" id="progress">
                <div class="progress-bar" id="progress-bar"></div>
            </div>
            <div class="progress-text" id="progress-text"></div>
            <div class="download-link" id="download-link">
                <a href="#" id="download-url">Download patched file</a>
            </div>
        </div>
        <script>
            function updateFileName(input) {
                const fileName = input.files[0] ? input.files[0].name : "No file selected";
                document.getElementById('file-name').textContent = fileName;
            }

            document.getElementById('upload-form').addEventListener('submit', async function(event) {
                event.preventDefault();
                const formData = new FormData(this);
                const progressBar = document.getElementById('progress-bar');
                const progressText = document.getElementById('progress-text');
                const progress = document.getElementById('progress');
                const patchButton = document.getElementById('patch-button');
                patchButton.style.display = 'none';
                progress.style.display = 'block';
                progressBar.style.width = '0%';
                progressText.textContent = 'Uploading...';

                const response = await fetch('/patch', {
                    method: 'POST',
                    body: formData,
                    onUploadProgress: function(progressEvent) {
                        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
                        progressBar.style.width = percentCompleted + '%';
                    }
                });

                const result = await response.json();
                if (result.permalink) {
                    progressBar.style.width = '100%';
                    progressText.textContent = 'Patching completed!';
                    const downloadLink = document.getElementById('download-link');
                    const downloadUrl = document.getElementById('download-url');
                    downloadUrl.href = result.permalink;
                    downloadLink.style.display = 'block';
                } else {
                    progressText.textContent = 'Error: ' + result.error;
                    patchButton.style.display = 'block';
                }
            });
        </script>
    </body>
    </html>
    '''

@app.route('/patch', methods=['POST'])
def patch():
    if 'file' not in request.files:
        return jsonify({"error": "No file part"}), 400
    file = request.files['file']
    if file.filename == '':
        return jsonify({"error": "No selected file"}), 400
    if not file.filename.endswith('.so'):
        return jsonify({"error": "Invalid file type. Only .so files are allowed."}), 400

    # Generate a unique file path
    file_uuid = str(uuid.uuid4())
    file_path = os.path.join('uploads', f"{file_uuid}_{file.filename}")
    file.save(file_path)

    # Determine the library name based on the checkbox
    library_name = "libbluetooth_qti.so" if 'qti' in request.form else "libbluetooth_jni.so"

    # Patch the file
    try:
        l2c_fcr_chk_chan_modes_address = get_symbol_address(file_path, "l2c_fcr_chk_chan_modes")
        patch_address(file_path, l2c_fcr_chk_chan_modes_address, "20008052c0035fd6")
        l2cu_send_peer_info_req_address = get_symbol_address(file_path, "l2cu_send_peer_info_req")
        patch_address(file_path, l2cu_send_peer_info_req_address, "c0035fd6")
    except Exception as e:
        logger.error(f"Error patching file: {str(e)}")
        return jsonify({"error": f"Error patching file: {str(e)}"}), 500

    # Create permalink
    permalink_id = str(uuid.uuid4())
    PATCHED_LIBRARIES[permalink_id] = {
        'file_path': file_path,
        'library_name': library_name,
        'timestamp': time.time()
    }

    # Save patch info
    save_patch_info(permalink_id, file_path)

    # Schedule deletion
    threading.Timer(PERMALINK_EXPIRY, delete_expired_permalink, args=[permalink_id]).start()
    logger.info(f"Permalink {permalink_id} created, will expire in {PERMALINK_EXPIRY} seconds")

    return jsonify({'permalink': f'/download/{permalink_id}'})

@app.route('/download/<permalink_id>', methods=['GET'])
def download(permalink_id):
    if permalink_id not in PATCHED_LIBRARIES:
        return "Permalink expired or invalid", 404

    file_path = PATCHED_LIBRARIES[permalink_id]['file_path']
    library_name = PATCHED_LIBRARIES[permalink_id]['library_name']
    if not os.path.exists(file_path):
        return "File not found", 404

    try:
        copy_file_to_src(file_path, library_name)
        zip_src_files()
    except Exception as e:
        logger.error(f"Error preparing download: {str(e)}")
        return f"Error preparing download: {str(e)}", 500

    resp = make_response(send_file('btl2capfix.zip', as_attachment=True))
    resp.headers['Content-Disposition'] = f'attachment; filename=btl2capfix.zip'
    return resp

def delete_expired_permalink(permalink_id):
    if permalink_id in PATCHED_LIBRARIES:
        file_path = PATCHED_LIBRARIES[permalink_id]['file_path']
        if os.path.exists(file_path):
            os.remove(file_path)
            logger.info(f"Deleted file: {file_path}")
        del PATCHED_LIBRARIES[permalink_id]
        logger.info(f"Permalink {permalink_id} expired and removed from PATCHED_LIBRARIES")

if not os.path.exists('uploads'):
    os.makedirs('uploads')
if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=8080)
