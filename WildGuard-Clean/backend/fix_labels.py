import os

fixed_count = 0
ok_count = 0
total_count = 0

def convert_seg_to_box(label_path):
    global fixed_count, ok_count, total_count
    total_count += 1

    with open(label_path, 'r') as f:
        lines = f.readlines()

    new_lines = []
    converted = False

    for line in lines:
        parts = line.strip().split()
        if len(parts) > 5:
            # Segmentation format: class x1 y1 x2 y2 ...
            cls = parts[0]
            coords = list(map(float, parts[1:]))

            xs = coords[0::2]
            ys = coords[1::2]

            # Convert polygon to bounding box
            x_min, x_max = min(xs), max(xs)
            y_min, y_max = min(ys), max(ys)

            # Convert to YOLO bbox format (cx, cy, w, h)
            cx = (x_min + x_max) / 2
            cy = (y_min + y_max) / 2
            w = x_max - x_min
            h = y_max - y_min

            new_line = f"{cls} {cx:.6f} {cy:.6f} {w:.6f} {h:.6f}\n"
            new_lines.append(new_line)
            converted = True
        else:
            # Already in bbox format
            new_lines.append(line)

    if converted:
        with open(label_path, 'w') as f:
            f.writelines(new_lines)
        fixed_count += 1
        print(f"[FIXED] Converted segmentation to bbox in: {label_path}")
    else:
        ok_count += 1
        print(f"[OK] Already bbox format: {label_path}")

def scan_and_fix_labels(root_folder):
    for subdir, _, files in os.walk(root_folder):
        for file in files:
            if file.endswith(".txt"):
                label_path = os.path.join(subdir, file)
                convert_seg_to_box(label_path)

if __name__ == "__main__":
    labels_folder = r"C:\Users\ASUS\backend part 2\final_dataset\labels"
    scan_and_fix_labels(labels_folder)

    print("\n--- SUMMARY ---")
    print(f"Total label files checked: {total_count}")
    print(f"Already bbox format: {ok_count}")
    print(f"Fixed from segmentation: {fixed_count}")