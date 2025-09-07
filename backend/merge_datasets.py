import os
import shutil

# ===== CONFIG =====
BASE_DIR = "datasets_raw"       # Folder containing all separate datasets
FINAL_DIR = "final_dataset"     # Where merged dataset will be created

# Mapping of dataset folder name → YOLO class ID
CLASS_ORDER = {
    "elephant_dataset": 0,
    "tiger_dataset": 1,
    "boar_dataset": 2,
    "deer_dataset": 3,
    "bison_dataset": 4,
    "bear_dataset": 5,
    "cheetah_dataset": 6
}

# Supported image extensions
IMG_EXTS = [".jpg", ".jpeg", ".png"]

# Create final directories
for split in ["train", "val"]:
    os.makedirs(os.path.join(FINAL_DIR, "images", split), exist_ok=True)
    os.makedirs(os.path.join(FINAL_DIR, "labels", split), exist_ok=True)

print("🟢 Starting dataset merge...")

def process_and_merge(dataset_folder, new_class_id):
    for split in ["train", "val"]:
        images_path = os.path.join(BASE_DIR, dataset_folder, "images", split)
        labels_path = os.path.join(BASE_DIR, dataset_folder, "labels", split)

        print(f"\n🔹 Processing {dataset_folder}/{split}...")
        print(f"Images path: {images_path}")
        print(f"Labels path: {labels_path}")

        if not os.path.exists(images_path) or not os.path.exists(labels_path):
            print(f"⚠ Skipping {dataset_folder}/{split} - path not found")
            continue

        img_files = [f for f in os.listdir(images_path) if os.path.splitext(f)[1].lower() in IMG_EXTS]
        if not img_files:
            print(f"⚠ No images found in {images_path}")
            continue

        for img_file in img_files:
            src_img = os.path.join(images_path, img_file)
            dst_img = os.path.join(FINAL_DIR, "images", split, f"{dataset_folder}_{img_file}")
            shutil.copy2(src_img, dst_img)
            print(f"✅ Copied image: {dst_img}")

            # Corresponding label
            label_file = os.path.splitext(img_file)[0] + ".txt"
            src_label = os.path.join(labels_path, label_file)
            dst_label = os.path.join(FINAL_DIR, "labels", split, f"{dataset_folder}_{label_file}")

            if not os.path.exists(src_label):
                print(f"⚠ Label missing for {img_file}, skipping label")
                continue

            with open(src_label, "r") as f:
                lines = f.readlines()

            new_lines = []
            for line in lines:
                parts = line.strip().split()
                if len(parts) > 0:
                    parts[0] = str(new_class_id)
                    new_lines.append(" ".join(parts))

            with open(dst_label, "w") as f:
                f.write("\n".join(new_lines))
            print(f"✅ Updated label: {dst_label}")

        print(f"🎯 Finished processing {dataset_folder}/{split} → class {new_class_id}")

# Merge all datasets
for folder_name, class_id in CLASS_ORDER.items():
    process_and_merge(folder_name, class_id)

print("\n🎉 All datasets merged successfully into 'final_dataset/'")