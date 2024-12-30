# AirPods like Normal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
#
# Copyright (C) 2024 Kavish Devar
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

import os
import matplotlib.pyplot as plt
import mplcursors

def hex_to_base10(hex_string):
    hex_values = hex_string.split()
    base10_values = [int(hex_value, 16) for hex_value in hex_values]
    return base10_values

def convert_file(input_filepath):
    data = []
    with open(input_filepath, 'r') as infile:
        for line in infile:
            base10_line = hex_to_base10(line.strip())
            data.append(base10_line)
    return data

def plot_data(data, title, directory):
    plt.figure()
    plt.title(title)
    # columns_to_remove = [0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 13, 14, 15, 16, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 29, 32, 35, 36, 37, 38, 43, 45, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 59, 46, 61, 63, 64, 65, 67, 68, 70, 71, 73, 74, 75, 77]
    columns_to_remove = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18 ]
    for i in range(len(data[0])):
        if i in columns_to_remove:
            continue
        plt.plot([row[i] for row in data], label=f'Column {i}', alpha=1, linewidth=1.2)
    
    plt.legend()
    plt.xlabel('Time')
    plt.ylabel('Value')
    
    # Add interactive cursor
    mplcursors.cursor(hover=True)
    
    plt.show()
    plt.close()

def process_files(directory):
    for filename in os.listdir(directory):
        if filename.endswith('.txt'):
            input_filepath = os.path.join(directory, filename)
            data = convert_file(input_filepath)
            plot_data(data, f'{filename}', directory)

# Directory containing the files
directory = 'yes'

# Process each file in the directory
process_files(directory)