"""
Export tennis analyzer metrics for frontend viewer
"""
import sys
import json
import numpy as np
from scipy.signal import savgol_filter

# Add parent directory to path
sys.path.insert(0, '/Users/ycy/Projects')

from tennis_analyzer_v2 import TennisSwingAnalyzer, AnalyzerConfig


def smooth_array(arr, window=15, polyorder=3):
    """Apply Savitzky-Golay smoothing"""
    if len(arr) < window:
        return arr
    if window % 2 == 0:
        window += 1
    return savgol_filter(arr, window, polyorder).tolist()

def get_video_duration(video_path: str) -> float:
    """Get actual video duration using ffprobe"""
    import subprocess
    result = subprocess.run(
        ['ffprobe', '-v', 'quiet', '-show_entries', 'format=duration',
         '-of', 'default=noprint_wrappers=1:nokey=1', video_path],
        capture_output=True, text=True
    )
    return float(result.stdout.strip())


def export_for_frontend(video_path: str, output_path: str):
    """Run analyzer and export metrics in frontend format"""

    print(f"Analyzing: {video_path}")

    config = AnalyzerConfig()
    analyzer = TennisSwingAnalyzer(config)
    analyzer.process_video(video_path)

    # Extract per-frame metrics
    metrics = analyzer.metrics

    # Calculate actual fps from duration (handles VFR videos)
    actual_duration = get_video_duration(video_path)
    fps = len(metrics) / actual_duration
    print(f"  Actual duration: {actual_duration:.2f}s, effective fps: {fps:.2f}")

    data = {
        'video_path': video_path.split('/')[-1],
        'fps': fps,
        'duration': actual_duration,
        'timestamps': [],
        'x_factor': [],
        'wrist_speed': [],
        'knee_angle': [],
        'elbow_angle': [],
        'weight_distribution': []
    }

    # Get wrist velocity from analyzer
    wrist_velocity = analyzer._wrist_velocity_ms if hasattr(analyzer, '_wrist_velocity_ms') else None

    for i, m in enumerate(metrics):
        # Use actual timestamps based on real duration
        data['timestamps'].append(i / fps)
        data['x_factor'].append(float(m.x_factor))

        # Use computed wrist velocity if available
        if wrist_velocity is not None and i < len(wrist_velocity):
            data['wrist_speed'].append(float(wrist_velocity[i]))
        else:
            data['wrist_speed'].append(0.0)

        # Use front knee angle
        if config.dominant_hand == "right":
            knee = m.right_knee_angle
        else:
            knee = m.left_knee_angle
        data['knee_angle'].append(float(knee))

        data['elbow_angle'].append(float(m.elbow_angle))
        data['weight_distribution'].append(float(m.weight_distribution))

    # Apply smoothing
    window = 15 if fps >= 60 else 11
    print(f"  Applying smoothing (window={window})...")

    data['x_factor'] = smooth_array(data['x_factor'], window)
    data['wrist_speed'] = smooth_array(data['wrist_speed'], window)
    data['knee_angle'] = smooth_array(data['knee_angle'], window)
    data['elbow_angle'] = smooth_array(data['elbow_angle'], window)
    data['weight_distribution'] = smooth_array(data['weight_distribution'], window)

    # Save to JSON
    with open(output_path, 'w') as f:
        json.dump(data, f)

    print(f"Exported to: {output_path}")
    print(f"  Frames: {len(metrics)}")
    print(f"  Duration: {data['duration']:.2f}s")
    print(f"  Max X-Factor: {max(data['x_factor'], key=abs):.1f}°")
    print(f"  Max Wrist Speed: {max(data['wrist_speed']):.1f} m/s")

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: python export_metrics.py <video_path> <output_json>")
        sys.exit(1)

    export_for_frontend(sys.argv[1], sys.argv[2])
