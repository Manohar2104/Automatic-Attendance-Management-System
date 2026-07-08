import subprocess
import json
import urllib.request
import time
import sys
import platform

FIND3_URL = "http://localhost:8005/data"
FAMILY = "smart_attendance"
DEVICE = "calibration_laptop"

def scan_wifi_linux():
    """Scan WiFi on Linux using nmcli."""
    try:
        # Run nmcli to list nearby networks with BSSID, SSID, and SIGNAL strength
        cmd = ["nmcli", "-t", "-f", "BSSID,SSID,SIGNAL", "dev", "wifi"]
        output = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8")
        
        wifi_data = {}
        for line in output.strip().split("\n"):
            if not line:
                continue
            parts = line.split(":")
            if len(parts) >= 3:
                # nmcli outputs BSSID with escaped backslashes, e.g., 'AA\:BB\:CC\:DD\:EE\:FF'
                bssid = (parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3] + ":" + parts[4] + ":" + parts[5]).replace("\\", "")
                ssid = parts[6]
                
                # Signal strength (percentage 0-100) to RSSI estimation (dBm)
                # Simple approximation: dBm = (Percentage / 2) - 100
                try:
                    signal = int(parts[7])
                    rssi = int((signal / 2) - 100)
                    wifi_data[bssid] = rssi
                except ValueError:
                    pass
        return wifi_data
    except Exception as e:
        print(f"Error scanning WiFi on Linux: {e}")
        return None

def main():
    if len(sys.argv) < 2:
        print("Usage:")
        print("  python tools/calibrate.py <location_name> [num_scans]")
        print("Example:")
        print("  python tools/calibrate.py \"Room 101\" 10")
        sys.exit(1)
        
    location = sys.argv[1]
    num_scans = int(sys.argv[2]) if len(sys.argv) > 2 else 10
    
    if platform.system() != "Linux":
        print(f"Error: WiFi scanning in this script is only supported on Linux (Current OS: {platform.system()}).")
        print("Please run this script on a Linux laptop with nmcli installed.")
        sys.exit(1)
        
    print(f"=== STARTING FIND3 CLASSROOM CALIBRATION ===")
    print(f"Targeting: {FIND3_URL}")
    print(f"Location:  {location}")
    print(f"Scans:     {num_scans} samples\n")
    
    successful_scans = 0
    for i in range(1, num_scans + 1):
        print(f"Scan {i}/{num_scans}: Scanning WiFi networks...")
        wifi_scan = scan_wifi_linux()
        
        if not wifi_scan:
            print("Warning: WiFi scan returned no results. Make sure WiFi is enabled and nmcli is installed.")
            time.sleep(3)
            continue
            
        print(f"Found {len(wifi_scan)} access points. Sending to FIND3...")
        
        # Prepare payload matching FIND3 API docs
        payload = {
            "d": DEVICE,
            "f": FAMILY,
            "t": int(time.time() * 1000),
            "l": location,
            "s": {
                "wifi": wifi_scan
            }
        }
        
        try:
            req = urllib.request.Request(
                FIND3_URL,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json"},
                method="POST"
            )
            with urllib.request.urlopen(req) as response:
                resp_data = json.loads(response.read().decode("utf-8"))
                if resp_data.get("success"):
                    print(f"SUCCESS: Sample registered for {location}.")
                    successful_scans += 1
                else:
                    print(f"FAILED: {resp_data.get('message')}")
        except Exception as e:
            print(f"HTTP Post error: {e}")
            
        if i < num_scans:
            print("Waiting 5 seconds for next scan...\n")
            time.sleep(5)
            
    print(f"\nCalibration finished. Registered {successful_scans}/{num_scans} samples.")
    if successful_scans > 0:
        print("\nTriggering machine learning model calibration...")
        try:
            calibrate_url = f"http://localhost:8005/api/v1/calibrate/{FAMILY}"
            with urllib.request.urlopen(calibrate_url) as response:
                resp_data = json.loads(response.read().decode("utf-8"))
                print(f"Calibrate response: {resp_data.get('message')}")
        except Exception as e:
            print(f"Could not trigger model calibration: {e}")
            
if __name__ == "__main__":
    main()
