import os
import matplotlib.pyplot as plt

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

def plot_data(data, title, save_path):
    # Transpose the data to get columns
    columns = list(zip(*data))
    # Filter out columns that stay the same across all rows
    filtered_columns = [column for column in columns if len(set(column)) > 1]
    # Generate time values for the x-axis
    time_values = [i * 35 for i in range(len(data))]
    
    # Plot in sets of 4 columns
    for start in range(0, len(filtered_columns), 4):
        end = start + 4
        subset_columns = filtered_columns[start:end]
        num_columns = len(subset_columns)
        fig, axs = plt.subplots(num_columns, 1, figsize=(10, 2 * num_columns), sharex=True)
        
        if num_columns == 1:
            axs = [axs]  # Ensure axs is iterable when there's only one subplot
        
        for i, column in enumerate(subset_columns):
            axs[i].plot(time_values, column, label=f'Column {start + i + 1}')
            axs[i].set_ylabel('Value')
            axs[i].legend()
        
        plt.xlabel('Time (ms)')
        plt.suptitle(f'{title} (Columns {start + 1} to {end})')
        
        # Save the plot as a PNG file
        plot_filename = f'{title.replace(".txt","")} - {start + 1} to {end}.png'
        plt.savefig(os.path.join(save_path, plot_filename))
        plt.close()

def process_files(directory):
    for filename in os.listdir(directory):
        if filename.endswith('.txt'):
            input_filepath = os.path.join(directory, filename)
            data = convert_file(input_filepath)
            plot_data(data, f'{filename}', directory)

# Directory containing the files
directory = '/Users/kavish/AirPodsLikeNormal/training data/Extracted Data/'

# Process each file in the directory
process_files(directory)