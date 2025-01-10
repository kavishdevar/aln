import logging
import os
import re
import shutil
import subprocess
import sys
import zipfile

# Define color codes for logging
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

# Custom logging formatter to include colors
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

def run_command(command):
    """
    Runs a shell command and logs the output.

    Args:
        command (str): The command to run.

    Returns:
        str: The standard output from the command.

    Raises:
        SystemExit: If the command fails.
    """
    logging.info(f"Running command: {command}")
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    if result.returncode != 0 and "Cannot determine entrypoint" not in result.stderr:
        logging.error(f"Command failed: {command}\n{result.stderr}")
        sys.exit(1)
    logging.info(f"Command output: {result.stdout}")
    return result.stdout

def get_symbol_address(file_path, symbol_name):
    """
    Gets the address of a symbol in a binary file using radare2.

    Args:
        file_path (str): The path to the binary file.
        symbol_name (str): The name of the symbol to find.

    Returns:
        str: The address of the symbol.

    Raises:
        SystemExit: If the symbol is not found.
    """
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
    """
    Patches a specific address in a binary file with given bytes using radare2.

    Args:
        file_path (str): The path to the binary file.
        address (str): The address to patch.
        patch_bytes (str): The bytes to write at the address.

    Raises:
        SystemExit: If the patching command fails.
    """
    logging.info(f"Patching address {address} with bytes: {patch_bytes}")
    run_command(f"radare2 -q -e bin.cache=true -w -c 's {address}; wx {patch_bytes}; wci' {file_path}")
    logging.info(f"Successfully patched address {address}")

def copy_file_to_src(file_path, library_name):
    """
    Copies a file to the 'src/' directory with the specified library name.

    Args:
        file_path (str): The path to the file to copy.
        library_name (str): The name to use for the copied library.
    """
    if os.path.exists('btl2capfix.zip'):
        os.remove('btl2capfix.zip')
    if os.path.exists('src/libbluetooth_jni.so'):
        os.remove('src/libbluetooth_jni.so')
    if os.path.exists('src/libbluetooth_qti.so'):
        os.remove('src/libbluetooth_qti.so')
    src_dir = 'src/'
    if not os.path.exists(src_dir):
        os.makedirs(src_dir)
    dest_path = os.path.join(src_dir, library_name)
    shutil.copy(file_path, dest_path)
    logging.info(f"Copied {file_path} to {dest_path}")

def zip_src_files():
    """
    Zips all files in the 'src/' directory into 'btl2capfix.zip', preserving symlinks without compression.
    """
    with zipfile.ZipFile('btl2capfix.zip', 'w', zipfile.ZIP_STORED, allowZip64=True) as zipf:
        for root, dirs, files in os.walk('src/'):
            for file in files:
                file_path = os.path.join(root, file)
                if os.path.islink(file_path):
                    link_target = os.readlink(file_path)
                    zip_info = zipfile.ZipInfo(os.path.relpath(file_path, 'src/'))
                    zip_info.create_system = 3  # Unix
                    zip_info.external_attr = 0o777 << 16
                    zip_info.external_attr |= 0xA000
                    zipf.writestr(zip_info, link_target)
                else:
                    zipf.write(file_path, os.path.relpath(file_path, 'src/'))
    logging.info("Zipped files under src/ into btl2capfix.zip")

def main():
    """
    Main function to execute the script. It performs the following steps:
    1. Copies the input file to the 'src/' directory.
    2. Patches specific addresses in the binary file.
    4. Zips the files in the 'src/' directory into 'btl2capfix.zip'.
    """
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
    logger = logging.getLogger()
    handler = logger.handlers[0]
    handler.setFormatter(ColoredFormatter('%(asctime)s - %(levelname)s - %(message)s'))

    if len(sys.argv) != 3:
        logging.error("Usage: python main.py <file_path> <library_name>")
        sys.exit(1)

    file_path = sys.argv[1]
    library_name = sys.argv[2]

    # Patch l2c_fcr_chk_chan_modes
    l2c_fcr_chk_chan_modes_address = get_symbol_address(file_path, "l2c_fcr_chk_chan_modes")
    patch_address(file_path, l2c_fcr_chk_chan_modes_address, "20008052c0035fd6")

    # Patch l2cu_send_peer_info_req
    l2cu_send_peer_info_req_address = get_symbol_address(file_path, "l2cu_send_peer_info_req")
    patch_address(file_path, l2cu_send_peer_info_req_address, "c0035fd6")

    # Copy file to src/
    copy_file_to_src(file_path, library_name)

    # Zip files under src/
    zip_src_files()

if __name__ == "__main__":
    main()