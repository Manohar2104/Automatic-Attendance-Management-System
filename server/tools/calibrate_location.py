"""
Calibration helper for FIND3 location fingerprinting.
Run this script while standing in a location (e.g. corridor, 211, 212) to train FIND3.
Usage:
    python tools/calibrate_location.py --location corridor --duration 120
"""
import sys
import os
import time
import argparse
import subprocess
import re
import json
import urllib.request

FIND3_URL = "http://127.0.0.1:8005/data"
FAMILY = "pes"
DEVICE_ID = "admin_calibration_device"


def scan_wifi_linux():
    """Perform a Wi-Fi scan on Linux using nmcli or iwlist."""
    bssids = {}
    try:
        # Try nmcli first
        cmd = ["nmcli", "-f", "BSSID,SIGNAL", "device", "wifi", "rescan"]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
        time.sleep(2)
        
        cmd = ["nmcli", "-t", "-f", "BSSID,SIGNAL", "device", "wifi", "list"]
        out = subprocess.check_output(cmd, text=True, timeout=5)
        for line in out.strip().split("\n"):
            if ":" in line:
                parts = line.split(":")
                if len(parts) >= 7:
                    bssid = ":".join(parts[:6]).upper()
                    signal_percent = int(parts[6]) if parts[6].isdigit() else 50
                    # Convert percent to approximate dBm (dBm = (percent / 2) - 100)
                    dbm = int((signal_percent / 2.0) - 100)
                    bssids[bssid] = dbm
    except Exception:
        pass

    if not bssids:
        try:
            # Fallback to iwlist
            out = subprocess.check_output(["sudo", "iwlist", "scanning"], text=True, timeout=5)
            cells = out.split("Cell ")
            for cell in cells[1:]:
                mac_match = re.search(r"Address:\s*([0-9A-Fa-f:]{17})", cell)
                signal_match = re.search(r"Signal level=(-\d+)", cell)
                if mac_match and signal_match:
                    mac = mac_match.group(1).upper()
                    dbm = int(signal_match.group(1))
                    bssids[mac] = dbm
        except Exception:
            pass

    return bssids


def send_calibration_sample(location: str, wifi_signals: dict):
    payload = {
        "d": DEVICE_ID,
        "f": FAMILY,
        "location": location,
        "t": int(time.time() * 1000),
        "s": {
            "wifi": wifi_signals
        }
    }
    
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        FIND3_URL,
        data=data,
        headers={"Content-Type": "application/json"}
    )
    
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            res_body = resp.read().decode("utf-8")
            return res_body
    except Exception as e:
        print(f"  ❌ Error posting to FIND3: {e}")
        return None


def main():
    parser = argparse.ArgumentParser(description="FIND3 Location Calibrator")
    parser.add_argument("--location", type=str, default="corridor", help="Location name (e.g. corridor, 211, 212)")
    parser.add_argument("--duration", type=int, default=120, help="Duration in seconds (default: 120s)")
    args = parser.parse_args()

    location = args.location
    duration = args.duration
    interval = 5  # Send sample every 5 seconds during calibration

    print(f"\n=======================================================")
    print(f" 📍 Starting FIND3 Calibration for: '{location}'")
    print(f" ⏱️  Duration: {duration} seconds (samples every {interval}s)")
    print(f" 🌐 Target FIND3 URL: {FIND3_URL}")
    print(f"=======================================================\n")
    print("➡️  Walk around the area now while samples are recorded...\n")

    start_time = time.time()
    sample_count = 0

    while (time.time() - start_time) < duration:
        sample_count += 1
        wifi_data = scan_wifi_linux()
        
        if wifi_data:
            resp = send_calibration_sample(location, wifi_data)
            print(f" Sample #{sample_count:02d} | Scanned {len(wifi_data)} APs | Location: '{location}' -> OK")
        else:
            print(f" Sample #{sample_count:02d} | ⚠️  No Wi-Fi APs detected in scan")
        
        time.sleep(interval)

    print(f"\n=======================================================")
    print(f" ✅ Calibration Completed for '{location}'!")
    print(f" 📊 Total Samples Recorded: {sample_count}")
    print(f" FIND3 is now trained to recognize '{location}'.")
    print(f"=======================================================\n")


if __name__ == "__main__":
    main()
