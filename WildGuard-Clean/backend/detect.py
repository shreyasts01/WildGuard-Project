import argparse
import os
import sys
import glob
import time
import csv
import base64
import shutil
import tempfile
import requests
import platform
from pathlib import Path
from datetime import datetime, timedelta
from collections import defaultdict, deque

import cv2
import firebase_admin
from firebase_admin import credentials, firestore
from ultralytics import YOLO

# ------------------------ FIREBASE INIT ------------------------
cred = credentials.Certificate(
    r"C:\\Users\\User\\WildGuard Project\\wildguard-b880e-firebase-adminsdk-fbsvc-0039a192ec.json"
)
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

# ------------------------ ImgBB API KEY ------------------------
IMGBB_API_KEY = "27298521410ecb9a317fdfc4a19f1a4f"

# ------------------------ Global utility ------------------------
FILE = Path(__file__).resolve()
ROOT = FILE.parents[0]

# ------------------------ Animal filter & label normalization ------------------------
TARGET_ANIMALS = {
    "elephant": ["elephant", "african elephant", "asian elephant"],
    "bison": ["bison", "american bison", "european bison"],
    "cheetah": ["cheetah"],
    "tiger": ["tiger", "bengal tiger", "siberian tiger"],
    "boar": ["boar", "wild boar", "hog", "pig"],
    "deer": ["deer", "white-tailed deer", "sika deer", "mule deer", "red deer", "chital"],
    "bear": ["bear", "brown bear", "black bear", "polar bear", "grizzly bear", "sloth bear"],
}
SYN_TO_CANON = {alt.lower(): canon for canon, alts in TARGET_ANIMALS.items() for alt in alts}
CANON_SET = set(TARGET_ANIMALS.keys())

def normalize_label(raw_label: str):
    return SYN_TO_CANON.get(raw_label.strip().lower(), None)

# ------------------------ Detection tracking (NEW FEATURE) ------------------------
DETECTION_HISTORY = defaultdict(lambda: deque(maxlen=5))
BLOCKED_UNTIL = {}

def should_upload(animal: str) -> bool:
    now = datetime.now()

    # Check cooldown
    if animal in BLOCKED_UNTIL:
        if now < BLOCKED_UNTIL[animal]:
            remaining = (BLOCKED_UNTIL[animal] - now).seconds
            print(f"[SKIP] {animal} still in cooldown ({remaining}s left)")
            return False
        else:
            print(f"[UNBLOCK] {animal} uploads are now allowed again")
            del BLOCKED_UNTIL[animal]

    # Add detection timestamp
    DETECTION_HISTORY[animal].append(now)

    # Check if 5 detections within 10 seconds
    if len(DETECTION_HISTORY[animal]) == 10:
        if (DETECTION_HISTORY[animal][-1] - DETECTION_HISTORY[animal][0]) <= timedelta(seconds=15):
            BLOCKED_UNTIL[animal] = now + timedelta(seconds=30)
            DETECTION_HISTORY[animal].clear()
            print(f"[BLOCKED] {animal} uploads paused until {BLOCKED_UNTIL[animal].strftime('%H:%M:%S')}")
            return False

    print(f"[UPLOAD] {animal} allowed")
    return True

# ------------------------ ImgBB upload ------------------------
def upload_to_imgbb(image_path, imgbb_api_key=IMGBB_API_KEY):
    with open(image_path, "rb") as img_file:
        encoded_string = base64.b64encode(img_file.read())
    url = "https://api.imgbb.com/1/upload"
    payload = {"key": imgbb_api_key, "image": encoded_string}
    response = requests.post(url, data=payload, timeout=30)
    data = response.json()
    if data.get("success"):
        return data["data"]["url"]
    else:
        print("Upload failed:", data)
        return None

# ------------------------ Firestore save ------------------------
def save_link_to_firebase(link, label):
    doc_ref = db.collection("images").document()
    doc_ref.set(
        {
            "img_url": link,
            "label": str(label),
            "timestamp": firestore.SERVER_TIMESTAMP,
        }
    )

# ------------------------ CSV logging ------------------------
def write_to_csv(csv_path: Path, image_name: str, prediction: str, confidence: float):
    data = {"Image Name": image_name, "Prediction": prediction, "Confidence": f"{confidence:.2f}"}
    file_exists = csv_path.exists()
    with open(csv_path, mode="a", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=data.keys())
        if not file_exists:
            writer.writeheader()
        writer.writerow(data)

# ------------------------ Draw util ------------------------
def draw_box_with_label(frame, xyxy, label_text, thickness=2):
    x1, y1, x2, y2 = map(int, xyxy)
    cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 220, 0), thickness)
    (tw, th), _ = cv2.getTextSize(label_text, cv2.FONT_HERSHEY_SIMPLEX, 0.7, 2)
    y_text = y1 - 8 if y1 - 8 > 20 else y1 + 20
    cv2.rectangle(frame, (x1, y_text - th - 6), (x1 + tw + 6, y_text + 4), (0, 220, 0), -1)
    cv2.putText(frame, label_text, (x1 + 3, y_text), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 0), 2)

# ------------------------ Core detection loop ------------------------
def process_frames(
    model,
    source,
    conf_thres=0.5,
    iou_thres=0.5,
    save_csv=False,
    csv_path: Path = None,
    device="",
    imgsz=640,
):

    def handle_frame(frame, frame_name_for_log="frame"):
        results = model.predict(source=frame, conf=conf_thres, iou=iou_thres, imgsz=imgsz, device=device, verbose=False)

        any_animal = False
        best_label, best_conf = None, -1.0

        for res in results:
            names = res.names
            for b in res.boxes:
                cls_idx = int(b.cls[0])
                raw_label = names.get(cls_idx, str(cls_idx))
                label = normalize_label(raw_label)
                conf = float(b.conf)
                if label in CANON_SET:
                    any_animal = True
                    if conf > best_conf:
                        best_conf, best_label = conf, label
                    x1, y1, x2, y2 = [int(v) for v in b.xyxy[0]]
                    draw_box_with_label(frame, (x1, y1, x2, y2), f"{label} {conf:.2f}")

        # --- Upload decision based on new rule ---
        if any_animal and best_label:
            if should_upload(best_label):
                tmpdir = tempfile.mkdtemp(prefix="wildguard_")
                tmp_img = os.path.join(tmpdir, f"det_{datetime.now().strftime('%Y%m%d_%H%M%S_%f')}.jpg")
                cv2.imwrite(tmp_img, frame)

                url = upload_to_imgbb(tmp_img)
                if url:
                    save_link_to_firebase(url, best_label)
                    if save_csv and csv_path is not None:
                        write_to_csv(csv_path, Path(tmp_img).name, best_label, best_conf if best_conf >= 0 else 0.0)
                    print(f"[UPLOADED] {best_label} ({best_conf:.2f}) -> {url}")

                try:
                    shutil.rmtree(tmpdir, ignore_errors=True)
                except Exception as e:
                    print("Temp cleanup failed:", e)

        return frame

    # ------------------- Source handling -------------------
    is_webcam = str(source).isdigit()
    is_stream_url = str(source).lower().startswith(("rtsp://", "rtmp://", "http://", "https://"))
    is_video_file = (isinstance(source, str) and Path(source).suffix.lower() in {".mp4", ".avi", ".mov", ".mkv", ".webm"})
    is_image = (isinstance(source, str) and Path(source).suffix.lower() in {".jpg", ".jpeg", ".png", ".bmp"})
    is_dir = (isinstance(source, str) and Path(source).exists() and Path(source).is_dir())

    if is_webcam or is_stream_url or is_video_file:
        cap = cv2.VideoCapture(0 if is_webcam else source)
        if not cap.isOpened():
            print(f"Error: Could not open video source: {source}")
            return

        window_title = "WildGuard YOLOv8 (Animals Only) - Press 'q' to quit"
        print("Starting stream. Press 'q' to quit.")

        buffer_size = 5
        while True:
            for _ in range(buffer_size):
                cap.grab()
            ok, frame = cap.read()
            if not ok:
                break
            frame = handle_frame(frame, "stream_frame")
            cv2.imshow(window_title, frame)
            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

        cap.release()
        cv2.destroyAllWindows()
        return

    files = []
    if is_image:
        files = [source]
    elif is_dir:
        exts = [".jpg", ".jpeg", ".png", ".bmp"]
        for e in exts:
            files.extend(glob.glob(str(Path(source) / f"*{e}")))
        files.sort()
    else:
        if any(ch in str(source) for ch in "*?[]"):
            files = glob.glob(str(source))
            files.sort()

    if files:
        for im_path in files:
            frame = cv2.imread(im_path)
            if frame is None:
                print(f"Warning: could not read image: {im_path}")
                continue
            frame = handle_frame(frame, Path(im_path).name)
            cv2.imshow("WildGuard YOLOv8 (Animals Only) - Images", frame)
            if cv2.waitKey(1) & 0xFF == ord("q"):
                break
        cv2.destroyAllWindows()
    else:
        print(f"Source not recognized or empty: {source}")

# ------------------------ Main runner ------------------------
def run(
    weights="yolov8x-oiv7.pt",
    source="0",
    imgsz=640,
    conf_thres=0.5,
    iou_thres=0.5,
    device="",
    save_csv=False,
    csv_out="predictions.csv",
):
    print(f"Loading YOLOv8 model: {weights}")
    model = YOLO(weights)

    csv_path = Path(csv_out) if save_csv else None
    process_frames(
        model=model,
        source=source,
        conf_thres=conf_thres,
        iou_thres=iou_thres,
        save_csv=save_csv,
        csv_path=csv_path,
        device=device,
        imgsz=imgsz,
    )

def parse_opt():
    parser = argparse.ArgumentParser()
    parser.add_argument("--weights", type=str, default="yolov8x-oiv7.pt", help="model path or name")
    parser.add_argument("--source", type=str, default="0", help="file/dir/URL/glob/screen/0(webcam)")
    parser.add_argument("--imgsz", type=int, default=640, help="inference size")
    parser.add_argument("--conf-thres", type=float, default=0.5, help="confidence threshold")
    parser.add_argument("--iou-thres", type=float, default=0.5, help="NMS IoU threshold")
    parser.add_argument("--device", type=str, default="", help="cuda device, i.e. 0 or 0,1,2,3 or cpu")
    parser.add_argument("--save-csv", action="store_true", help="save results to CSV")
    parser.add_argument("--csv-out", type=str, default="predictions.csv", help="CSV output path")
    opt = parser.parse_args()
    return opt

def main():
    opt = parse_opt()
    run(
        weights=opt.weights,
        source=opt.source,
        imgsz=opt.imgsz,
        conf_thres=opt.conf_thres,
        iou_thres=opt.iou_thres,
        device=opt.device,
        save_csv=opt.save_csv,
        csv_out=opt.csv_out,
    )

if __name__ == "__main__":
    main()
