"""
Swing phase analyzer for BNO085 IMU event packets.

Segments a racquet swing into phases (preparation, acceleration, peak,
deceleration, follow-through) using gyroscope magnitude and its derivative.

Usage:
    from swing_analyzer import analyze_swing

    result = analyze_swing(samples)
    # samples = [{"t": ms, "gyro": {"x","y","z"}, "accel": {"x","y","z"}}, ...]
"""

import numpy as np
from scipy.signal import butter, sosfiltfilt, find_peaks

# ── Filter parameters ──────────────────────────────────────────────────
SAMPLE_RATE_HZ = 400
CUTOFF_HZ = 30          # low-pass cutoff
FILTER_ORDER = 2

# ── Phase detection thresholds ─────────────────────────────────────────
ACCEL_DERIV_THRESH = 0.05   # fraction of peak derivative to mark acceleration start
DECEL_DERIV_THRESH = 0.05   # fraction of peak negative derivative to mark decel end
MIN_PEAK_GYRO_RAD = 5.0     # ignore swings below this gyro magnitude (rad/s)


def _build_filter():
    """Pre-compute Butterworth second-order sections."""
    nyq = SAMPLE_RATE_HZ / 2.0
    sos = butter(FILTER_ORDER, CUTOFF_HZ / nyq, btype="low", output="sos")
    return sos

_SOS = _build_filter()


def _extract_arrays(samples):
    """Convert list-of-dicts into numpy arrays."""
    n = len(samples)
    t = np.empty(n)
    gyro = np.empty((n, 3))
    accel = np.empty((n, 3))

    for i, s in enumerate(samples):
        t[i] = s["t"]
        gyro[i] = [s["gyro"]["x"], s["gyro"]["y"], s["gyro"]["z"]]
        accel[i] = [s["accel"]["x"], s["accel"]["y"], s["accel"]["z"]]

    return t, gyro, accel


def _smooth(signal):
    """Apply zero-phase Butterworth low-pass filter."""
    if len(signal) < 15:          # sosfiltfilt needs padding
        return signal.copy()
    return sosfiltfilt(_SOS, signal)


def _find_phase_boundaries(t_ms, gyro_mag_smooth):
    """
    Return phase boundary timestamps using the first derivative of gyro magnitude.

    Returns dict with keys:
        accel_start_ms, peak_ms, decel_end_ms
    or None if no valid swing detected.
    """
    dt = np.gradient(t_ms) / 1000.0                    # seconds
    deriv = np.gradient(gyro_mag_smooth) / dt           # rad/s^2

    # Find the dominant peak in the smoothed gyro magnitude
    peaks, props = find_peaks(
        gyro_mag_smooth,
        height=MIN_PEAK_GYRO_RAD,
        prominence=MIN_PEAK_GYRO_RAD * 0.3,
        distance=int(SAMPLE_RATE_HZ * 0.05),           # at least 50 ms apart
    )

    if len(peaks) == 0:
        return None

    # Pick the tallest peak
    best = peaks[np.argmax(props["peak_heights"])]
    peak_ms = t_ms[best]

    # ── Acceleration start: walk left from peak until gyro mag drops
    #    below a fraction of the peak value ──
    peak_val = gyro_mag_smooth[best]
    accel_thresh = peak_val * 0.10   # 10% of peak
    accel_start_idx = 0
    for i in range(best - 1, -1, -1):
        if gyro_mag_smooth[i] < accel_thresh:
            accel_start_idx = i
            break

    # ── Deceleration end: walk right from peak until gyro mag drops
    #    below the same fraction ──
    decel_end_idx = len(gyro_mag_smooth) - 1
    for i in range(best + 1, len(gyro_mag_smooth)):
        if gyro_mag_smooth[i] < accel_thresh:
            decel_end_idx = i
            break

    return {
        "accel_start_idx": int(accel_start_idx),
        "peak_idx": int(best),
        "decel_end_idx": int(decel_end_idx),
        "accel_start_ms": float(t_ms[accel_start_idx]),
        "peak_ms": float(peak_ms),
        "decel_end_ms": float(t_ms[decel_end_idx]),
    }


def analyze_swing(samples):
    """
    Analyze a single swing event.

    Parameters
    ----------
    samples : list[dict]
        Event packet samples, each with keys "t", "gyro", "accel".
        Typically ~200 samples at 400 Hz (500 ms window).

    Returns
    -------
    dict with:
        - phases        : per-phase start/end timestamps
        - peak_gyro     : peak angular velocity (rad/s and deg/s)
        - peak_accel    : peak linear acceleration (m/s^2)
        - swing_duration_ms : from acceleration start to deceleration end
        - gyro_mag      : list of smoothed gyro magnitude values (for plotting)
        - accel_mag     : list of smoothed accel magnitude values
        - t_ms          : list of timestamps corresponding to magnitude arrays
        - raw_gyro_mag  : list of unfiltered gyro magnitudes
    """
    if not samples or len(samples) < 10:
        return {"error": "not enough samples", "phases": None}

    t, gyro, accel = _extract_arrays(samples)

    # Magnitudes
    gyro_mag_raw = np.linalg.norm(gyro, axis=1)
    accel_mag_raw = np.linalg.norm(accel, axis=1)

    # Smooth
    gyro_mag = _smooth(gyro_mag_raw)
    accel_mag = _smooth(accel_mag_raw)

    # Phase detection
    boundaries = _find_phase_boundaries(t, gyro_mag)

    if boundaries is None:
        return {
            "error": "no swing detected (gyro peak below threshold)",
            "phases": None,
            "peak_gyro_rad_s": float(np.max(gyro_mag)),
            "peak_gyro_deg_s": float(np.max(gyro_mag) * 57.2958),
            "t_ms": t.tolist(),
            "gyro_mag": gyro_mag.tolist(),
            "accel_mag": accel_mag.tolist(),
            "raw_gyro_mag": gyro_mag_raw.tolist(),
        }

    t_first = float(t[0])
    t_last = float(t[-1])

    phases = {
        "preparation": {
            "start_ms": t_first,
            "end_ms": boundaries["accel_start_ms"],
        },
        "acceleration": {
            "start_ms": boundaries["accel_start_ms"],
            "end_ms": boundaries["peak_ms"],
        },
        "peak": {
            "t_ms": boundaries["peak_ms"],
            "gyro_mag_rad_s": float(gyro_mag[boundaries["peak_idx"]]),
            "gyro_mag_deg_s": float(gyro_mag[boundaries["peak_idx"]] * 57.2958),
            "accel_mag_m_s2": float(accel_mag[boundaries["peak_idx"]]),
        },
        "deceleration": {
            "start_ms": boundaries["peak_ms"],
            "end_ms": boundaries["decel_end_ms"],
        },
        "follow_through": {
            "start_ms": boundaries["decel_end_ms"],
            "end_ms": t_last,
        },
    }

    swing_dur = boundaries["decel_end_ms"] - boundaries["accel_start_ms"]

    return {
        "phases": phases,
        "peak_gyro_rad_s": float(np.max(gyro_mag)),
        "peak_gyro_deg_s": float(np.max(gyro_mag) * 57.2958),
        "peak_accel_m_s2": float(np.max(accel_mag)),
        "swing_duration_ms": float(swing_dur),
        "t_ms": t.tolist(),
        "gyro_mag": gyro_mag.tolist(),
        "accel_mag": accel_mag.tolist(),
        "raw_gyro_mag": gyro_mag_raw.tolist(),
        "boundary_indices": {
            "accel_start": boundaries["accel_start_idx"],
            "peak": boundaries["peak_idx"],
            "decel_end": boundaries["decel_end_idx"],
        },
    }


# ── CLI quick test ─────────────────────────────────────────────────────
if __name__ == "__main__":
    # Generate a synthetic swing for testing
    np.random.seed(42)
    n = 200
    t_test = np.linspace(0, 500, n)  # 500 ms

    # Bell-curve-ish gyro profile simulating a swing
    center = 250
    width = 80
    gyro_profile = 35 * np.exp(-0.5 * ((t_test - center) / width) ** 2)
    noise = np.random.normal(0, 0.5, n)
    gyro_profile += noise

    fake_samples = []
    for i in range(n):
        g = float(gyro_profile[i])
        fake_samples.append({
            "t": float(t_test[i]),
            "gyro": {"x": g * 0.6, "y": g * 0.7, "z": g * 0.4},
            "accel": {"x": g * 1.5, "y": g * 0.8, "z": 9.8 + noise[i]},
        })

    result = analyze_swing(fake_samples)

    print("=== Swing Analysis Result ===")
    if result.get("phases"):
        for name, info in result["phases"].items():
            print(f"  {name:20s}: {info}")
        print(f"\n  Peak gyro : {result['peak_gyro_rad_s']:.1f} rad/s "
              f"({result['peak_gyro_deg_s']:.0f} deg/s)")
        print(f"  Peak accel: {result['peak_accel_m_s2']:.1f} m/s^2")
        print(f"  Swing dur : {result['swing_duration_ms']:.0f} ms")
    else:
        print(f"  No swing detected: {result.get('error')}")
