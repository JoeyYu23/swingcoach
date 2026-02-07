"""
Swing event visualizer.

Generates a two-panel time-series plot (gyro + accel magnitudes)
with swing phases shaded and annotated.  Saves PNGs to disk so
the server is never blocked by a GUI window.

Usage:
    from swing_visualizer import plot_swing
    plot_swing(analysis_result, event_number=1)
"""

import os
import numpy as np
import matplotlib
matplotlib.use("Agg")                        # no GUI backend
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

# ── Output directory ───────────────────────────────────────────────────
PLOT_DIR = "swing_plots"

# ── Phase colours (pastel so the signal line stays readable) ───────────
PHASE_COLORS = {
    "preparation":   "#cce5ff",   # light blue
    "acceleration":  "#d4edda",   # light green
    "peak":          "#fff3cd",   # light yellow
    "deceleration":  "#f8d7da",   # light red
    "follow_through": "#e2e3e5", # light grey
}

PHASE_LABELS = {
    "preparation":    "Prep",
    "acceleration":   "Accel",
    "peak":           "Peak",
    "deceleration":   "Decel",
    "follow_through": "Follow-through",
}


def plot_swing(result, event_number=0, out_dir=PLOT_DIR):
    """
    Save a two-panel PNG for one swing event.

    Parameters
    ----------
    result : dict
        Return value of ``analyze_swing()``.
    event_number : int
        Sequential event counter (used in filename and title).
    out_dir : str
        Directory to save PNGs into.

    Returns
    -------
    str  – path to the saved PNG, or None on error.
    """
    phases = result.get("phases")

    if "t_ms" not in result:
        return None

    os.makedirs(out_dir, exist_ok=True)

    t        = np.asarray(result["t_ms"])
    gyro     = np.asarray(result["gyro_mag"])
    gyro_raw = np.asarray(result["raw_gyro_mag"])
    accel    = np.asarray(result["accel_mag"])

    # Normalise time so plot starts at 0
    t0 = t[0]
    t_rel = t - t0

    # ── Create figure ──────────────────────────────────────────────
    fig, (ax_g, ax_a) = plt.subplots(
        2, 1, figsize=(10, 6), sharex=True,
        gridspec_kw={"hspace": 0.12},
    )
    title = f"Swing Event #{event_number}"
    if phases is None:
        title += "  (no phases detected)"
    fig.suptitle(title, fontsize=14, fontweight="bold", y=0.97)

    # ── Shade phase regions on both axes (only if phases detected) ─
    if phases is not None:
        phase_spans = [
            ("preparation",    phases["preparation"]["start_ms"],    phases["preparation"]["end_ms"]),
            ("acceleration",   phases["acceleration"]["start_ms"],   phases["acceleration"]["end_ms"]),
            ("deceleration",   phases["deceleration"]["start_ms"],   phases["deceleration"]["end_ms"]),
            ("follow_through", phases["follow_through"]["start_ms"], phases["follow_through"]["end_ms"]),
        ]

        for ax in (ax_g, ax_a):
            for name, s_ms, e_ms in phase_spans:
                ax.axvspan(s_ms - t0, e_ms - t0,
                           color=PHASE_COLORS[name], alpha=0.55, zorder=0)

        # Peak line
        peak_t_rel = phases["peak"]["t_ms"] - t0
        for ax in (ax_g, ax_a):
            ax.axvline(peak_t_rel, color="#e67e22", ls="--", lw=1.5, alpha=0.8, zorder=1)

    # ── Gyro panel ─────────────────────────────────────────────────
    ax_g.plot(t_rel, gyro_raw, color="#adb5bd", lw=0.8, label="Raw", zorder=2)
    ax_g.plot(t_rel, gyro, color="#0d6efd", lw=2, label="Filtered", zorder=3)
    ax_g.set_ylabel("Gyro magnitude (rad/s)")
    ax_g.legend(loc="upper right", fontsize=8)

    # Annotate peak value (only if phases detected)
    if phases is not None:
        peak_val = phases["peak"]["gyro_mag_deg_s"]
        ax_g.annotate(
            f"{peak_val:.0f} deg/s",
            xy=(peak_t_rel, phases["peak"]["gyro_mag_rad_s"]),
            xytext=(15, 10), textcoords="offset points",
            fontsize=9, fontweight="bold", color="#e67e22",
            arrowprops=dict(arrowstyle="->", color="#e67e22", lw=1.2),
        )

    # ── Accel panel ────────────────────────────────────────────────
    ax_a.plot(t_rel, accel, color="#dc3545", lw=2, zorder=3)
    ax_a.set_ylabel("Accel magnitude (m/s\u00b2)")
    ax_a.set_xlabel("Time (ms)")

    # ── Phase labels at top of gyro panel ──────────────────────────
    if phases is not None:
        y_top = ax_g.get_ylim()[1]
        for name, s_ms, e_ms in phase_spans:
            mid = ((s_ms + e_ms) / 2.0) - t0
            ax_g.text(mid, y_top * 0.92, PHASE_LABELS[name],
                      ha="center", va="top", fontsize=8, fontstyle="italic",
                      color="#495057")

    # ── Styling ────────────────────────────────────────────────────
    for ax in (ax_g, ax_a):
        ax.grid(axis="y", alpha=0.3)
        ax.set_xlim(t_rel[0], t_rel[-1])

    # Duration annotation
    dur = result.get("swing_duration_ms", 0)
    fig.text(0.99, 0.01, f"Swing duration: {dur:.0f} ms", ha="right",
             fontsize=8, color="#6c757d")

    # ── Save ───────────────────────────────────────────────────────
    path = os.path.join(out_dir, f"swing_{event_number:04d}.png")
    fig.savefig(path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    return path


# ── Quick self-test with synthetic data ────────────────────────────────
if __name__ == "__main__":
    from swing_analyzer import analyze_swing

    np.random.seed(42)
    n = 200
    t_test = np.linspace(0, 500, n)
    center, width = 250, 80
    gyro_profile = 35 * np.exp(-0.5 * ((t_test - center) / width) ** 2)
    noise = np.random.normal(0, 0.5, n)
    gyro_profile += noise

    samples = []
    for i in range(n):
        g = float(gyro_profile[i])
        samples.append({
            "t": float(t_test[i]),
            "gyro": {"x": g * 0.6, "y": g * 0.7, "z": g * 0.4},
            "accel": {"x": g * 1.5, "y": g * 0.8, "z": 9.8 + noise[i]},
        })

    result = analyze_swing(samples)
    path = plot_swing(result, event_number=0)
    print(f"Saved test plot to: {path}")
