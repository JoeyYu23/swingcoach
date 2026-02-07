"""
Dual-mode test server for BNO085 sensor data.
Handles both "live" (200Hz) and "event" (400Hz swing capture) packets.
Run with: python server.py
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
from datetime import datetime, timezone
import json
import time
import threading
import socket
import os

from swing_analyzer import analyze_swing
from swing_visualizer import plot_swing

# Stats
live_count = 0
live_samples = 0
event_count = 0
start_time = None

# Logging controls
PRINT_LIVE_EVERY = 20        # print every N live packets
PRINT_EVENT_DETAILS = False  # per-sample event dump (very slow)
LIVE_UDP_PORT = 7104


class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        global live_count, live_samples, event_count, start_time

        # Handle video upload endpoint
        if self.path == "/upload-video":
            self.handle_video_upload()
            return

        # Handle sensor data (existing functionality)
        length = int(self.headers["Content-Length"])
        raw = json.loads(self.rfile.read(length))

        # Respond immediately with explicit Content-Length to avoid chunked encoding
        self.send_response(200)
        self.send_header("Content-Length", "2")
        self.end_headers()
        self.wfile.write(b"OK")

        now = datetime.now()
        if start_time is None:
            start_time = time.time()

        # Detect packet format
        if isinstance(raw, dict) and "type" in raw:
            pkt_type = raw["type"]
            samples = raw.get("samples", [])
        else:
            pkt_type = "live"
            samples = raw

        if pkt_type == "event":
            event_count += 1
            trigger_t = raw.get("trigger_t", 0)

            t_str = datetime.fromtimestamp(trigger_t / 1000.0, tz=timezone.utc).strftime(
                "%H:%M:%S.%f")[:-3] if trigger_t else "?"

            print(f"\n{'='*70}")
            print(f"  SWING EVENT #{event_count} | "
                  f"{len(samples)} samples @ 400Hz | trigger at {t_str}")

            if samples:
                gyro_mags = [
                    (s["gyro"]["x"]**2 + s["gyro"]["y"]**2 + s["gyro"]["z"]**2)**0.5
                    for s in samples
                ]
                peak_gyro = max(gyro_mags)
                peak_idx = gyro_mags.index(peak_gyro)
                t_first = samples[0]["t"]
                t_last = samples[-1]["t"]
                print(f"  Duration: {t_last - t_first}ms | "
                      f"Peak gyro: {peak_gyro:.1f} rad/s ({peak_gyro * 57.3:.0f} deg/s) "
                      f"at sample {peak_idx}/{len(samples)}")
                if PRINT_EVENT_DETAILS:
                    print(f"{'='*70}")
                    for s in samples:
                        t_sec = s["t"] / 1000.0
                        t_str = datetime.fromtimestamp(t_sec, tz=timezone.utc).strftime(
                            "%H:%M:%S.%f")[:-3]
                        gm = (s["gyro"]["x"]**2 + s["gyro"]["y"]**2 + s["gyro"]["z"]**2)**0.5
                        am = (s["accel"]["x"]**2 + s["accel"]["y"]**2 + s["accel"]["z"]**2)**0.5
                        print(
                            f"  [{t_str}] "
                            f"|gyro|={gm:6.1f}  "
                            f"|accel|={am:6.1f}  "
                            f"gyro=({s['gyro']['x']:7.2f},{s['gyro']['y']:7.2f},{s['gyro']['z']:7.2f})"
                        )

            # Analyze swing phases
            result = analyze_swing(samples)
            if result.get("phases"):
                phases = result["phases"]
                print(f"  Phases:")
                print(f"    Preparation:    {phases['preparation']['start_ms']:.0f} – {phases['preparation']['end_ms']:.0f} ms")
                print(f"    Acceleration:   {phases['acceleration']['start_ms']:.0f} – {phases['acceleration']['end_ms']:.0f} ms")
                print(f"    Peak:           {phases['peak']['t_ms']:.0f} ms | {phases['peak']['gyro_mag_deg_s']:.0f} deg/s")
                print(f"    Deceleration:   {phases['deceleration']['start_ms']:.0f} – {phases['deceleration']['end_ms']:.0f} ms")
                print(f"    Follow-through: {phases['follow_through']['start_ms']:.0f} – {phases['follow_through']['end_ms']:.0f} ms")
                print(f"  Swing duration: {result['swing_duration_ms']:.0f} ms")
            else:
                print(f"  No swing detected: {result.get('error')}")

            # Always generate a plot for every swing event
            path = plot_swing(result, event_number=event_count)
            if path:
                print(f"  Plot saved: {path}")

            print(f"{'='*70}\n")

        else:  # live
            live_count += 1
            live_samples += len(samples)
            elapsed = time.time() - start_time if start_time else 0
            rate = live_samples / elapsed if elapsed > 0 else 0

            if live_count % PRINT_LIVE_EVERY == 0:
                print(f"[{now.strftime('%H:%M:%S.%f')[:-3]}] "
                      f"LIVE #{live_count} | "
                      f"{len(samples)} smp | "
                      f"Rate: {rate:.0f} smp/s | "
                      f"Events so far: {event_count}")

    def handle_video_upload(self):
        """Handle video file upload from phone."""
        try:
            # Create videos directory if it doesn't exist
            video_dir = "videos"
            if not os.path.exists(video_dir):
                os.makedirs(video_dir)

            # Get content length and filename from headers
            content_length = int(self.headers["Content-Length"])
            filename = self.headers.get("X-Filename", f"video_{int(time.time() * 1000)}.mp4")

            # Read the video data
            video_data = self.rfile.read(content_length)

            # Save to file
            filepath = os.path.join(video_dir, filename)
            with open(filepath, "wb") as f:
                f.write(video_data)

            # Respond with success
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            response = json.dumps({
                "status": "success",
                "filename": filename,
                "size": len(video_data),
                "path": filepath
            })
            self.wfile.write(response.encode())

            print(f"\n[{datetime.now().strftime('%H:%M:%S')}] VIDEO UPLOADED: {filename} ({len(video_data) / 1024 / 1024:.2f} MB)")

        except Exception as e:
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            error_response = json.dumps({"status": "error", "message": str(e)})
            self.wfile.write(error_response.encode())
            print(f"\n[ERROR] Video upload failed: {e}")

    def log_message(self, format, *args):
        pass


def udp_live_server():
    global live_count, live_samples, start_time
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("0.0.0.0", LIVE_UDP_PORT))
    print(f"UDP live server listening on 0.0.0.0:{LIVE_UDP_PORT}")

    while True:
        data, _addr = sock.recvfrom(65535)
        try:
            raw = json.loads(data.decode("utf-8"))
        except Exception:
            continue

        if isinstance(raw, dict) and raw.get("type") == "live":
            samples = raw.get("samples", [])
        else:
            samples = raw if isinstance(raw, list) else []

        if start_time is None:
            start_time = time.time()

        live_count += 1
        live_samples += len(samples)
        elapsed = time.time() - start_time if start_time else 0
        rate = live_samples / elapsed if elapsed > 0 else 0

        if live_count % PRINT_LIVE_EVERY == 0:
            now = datetime.now()
            print(f"[{now.strftime('%H:%M:%S.%f')[:-3]}] "
                  f"LIVE #{live_count} | "
                  f"{len(samples)} smp | "
                  f"Rate: {rate:.0f} smp/s | "
                  f"Events so far: {event_count}")


if __name__ == "__main__":
    port = 7103
    print(f"Dual-mode server ready on 0.0.0.0:{port}")
    print(f"  Sensor Data (POST /): ")
    print(f"    - Live: 200Hz continuous stream")
    print(f"    - Event: 400Hz swing capture (200ms pre + 300ms post)")
    print(f"  Video Upload (POST /upload-video): Accept MP4/MOV files from phone")
    print(f"Waiting for data...\n")
    threading.Thread(target=udp_live_server, daemon=True).start()
    ThreadedHTTPServer(("0.0.0.0", port), Handler).serve_forever()
