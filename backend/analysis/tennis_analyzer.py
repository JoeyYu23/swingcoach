"""
Tennis Swing Analyzer
Analyzes tennis swing mechanics using pose estimation data.
"""

from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from enum import Enum
import numpy as np

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pose import PoseResult, AngleCalculator


class SwingPhase(Enum):
    """Tennis swing phases."""
    READY = "ready"
    PREPARATION = "preparation"
    BACKSWING = "backswing"
    FORWARD_SWING = "forward_swing"
    CONTACT = "contact"
    FOLLOW_THROUGH = "follow_through"


@dataclass
class SwingMetrics:
    """Metrics for a single frame of tennis swing."""
    frame_index: int
    phase: SwingPhase

    # Joint angles
    racket_elbow_angle: Optional[float] = None
    racket_shoulder_angle: Optional[float] = None
    front_knee_angle: Optional[float] = None
    back_knee_angle: Optional[float] = None

    # Body metrics
    torso_rotation: Optional[float] = None
    hip_rotation: Optional[float] = None
    shoulder_tilt: Optional[float] = None
    weight_distribution: Optional[float] = None  # -1 to 1 (back to front)


@dataclass
class SwingAnalysisResult:
    """Complete analysis result for a tennis swing."""
    total_frames: int
    phases: Dict[SwingPhase, Tuple[int, int]]  # phase -> (start_frame, end_frame)
    metrics_per_frame: List[SwingMetrics]

    # Key insights
    max_torso_rotation: float
    contact_elbow_angle: float
    contact_knee_angle: float
    follow_through_completion: float  # 0-100%

    # Issues detected
    issues: List[str]
    recommendations: List[str]
    overall_score: float  # 0-100


class TennisSwingAnalyzer:
    """Analyzes tennis swing mechanics from pose data."""

    # Ideal ranges for forehand (can be customized)
    IDEAL_RANGES = {
        'contact_elbow_angle': (150, 170),  # Slightly bent at contact
        'contact_knee_angle': (140, 160),   # Good leg drive
        'torso_rotation': (45, 90),         # Hip-shoulder separation
        'follow_through_shoulder': (120, 180),  # Full follow-through
    }

    def __init__(self, handedness: str = 'right'):
        """
        Initialize analyzer.

        Args:
            handedness: 'right' or 'left' handed player
        """
        self.handedness = handedness
        self.angle_calculator = AngleCalculator(use_3d=True)

        # Determine racket arm based on handedness
        self.racket_side = 'right' if handedness == 'right' else 'left'
        self.support_side = 'left' if handedness == 'right' else 'right'

    def analyze_frame(self, pose_result: PoseResult, frame_index: int) -> SwingMetrics:
        """
        Analyze a single frame.

        Args:
            pose_result: Pose estimation result
            frame_index: Frame number

        Returns:
            SwingMetrics for this frame
        """
        # Calculate joint angles
        angles = self.angle_calculator.calculate_all_angles(pose_result)

        # Calculate body metrics
        torso_rotation = self._calculate_torso_rotation(pose_result)

        # Determine phase based on angles
        phase = self._detect_phase(angles, torso_rotation)

        return SwingMetrics(
            frame_index=frame_index,
            phase=phase,
            racket_elbow_angle=angles.get(f'{self.racket_side}_elbow'),
            racket_shoulder_angle=angles.get(f'{self.racket_side}_shoulder'),
            front_knee_angle=angles.get(f'{self.racket_side}_knee'),
            back_knee_angle=angles.get(f'{self.support_side}_knee'),
            torso_rotation=torso_rotation,
            shoulder_tilt=self.angle_calculator.calculate_shoulder_tilt(pose_result),
        )

    def analyze_swing(self, pose_results: List[PoseResult]) -> SwingAnalysisResult:
        """
        Analyze complete swing sequence.

        Args:
            pose_results: List of pose results for each frame

        Returns:
            Complete swing analysis
        """
        metrics_per_frame = []

        for i, pose in enumerate(pose_results):
            metrics = self.analyze_frame(pose, i)
            metrics_per_frame.append(metrics)

        # Detect phase boundaries
        phases = self._detect_phase_boundaries(metrics_per_frame)

        # Extract key metrics
        max_torso = max((m.torso_rotation or 0) for m in metrics_per_frame)

        # Find contact frame (highest forward motion)
        contact_idx = self._find_contact_frame(metrics_per_frame, phases)
        contact_metrics = metrics_per_frame[contact_idx] if contact_idx else metrics_per_frame[len(metrics_per_frame)//2]

        # Analyze issues
        issues, recommendations = self._analyze_issues(metrics_per_frame, phases)

        # Calculate score
        score = self._calculate_score(metrics_per_frame, phases)

        return SwingAnalysisResult(
            total_frames=len(pose_results),
            phases=phases,
            metrics_per_frame=metrics_per_frame,
            max_torso_rotation=max_torso,
            contact_elbow_angle=contact_metrics.racket_elbow_angle or 0,
            contact_knee_angle=contact_metrics.front_knee_angle or 0,
            follow_through_completion=self._calculate_follow_through(metrics_per_frame, phases),
            issues=issues,
            recommendations=recommendations,
            overall_score=score,
        )

    def _calculate_torso_rotation(self, pose_result: PoseResult) -> Optional[float]:
        """Calculate torso rotation angle."""
        left_shoulder = pose_result.get_keypoint('left_shoulder')
        right_shoulder = pose_result.get_keypoint('right_shoulder')
        left_hip = pose_result.get_keypoint('left_hip')
        right_hip = pose_result.get_keypoint('right_hip')

        if not all([left_shoulder, right_shoulder, left_hip, right_hip]):
            return None

        # Shoulder vector
        shoulder_vec = np.array([
            right_shoulder.x - left_shoulder.x,
            right_shoulder.y - left_shoulder.y
        ])

        # Hip vector
        hip_vec = np.array([
            right_hip.x - left_hip.x,
            right_hip.y - left_hip.y
        ])

        # Angle between them
        dot = np.dot(shoulder_vec, hip_vec)
        norm_s = np.linalg.norm(shoulder_vec)
        norm_h = np.linalg.norm(hip_vec)

        if norm_s < 1e-8 or norm_h < 1e-8:
            return None

        cosine = np.clip(dot / (norm_s * norm_h), -1.0, 1.0)
        return np.degrees(np.arccos(cosine))

    def _detect_phase(self, angles: Dict, torso_rotation: Optional[float]) -> SwingPhase:
        """Detect swing phase based on current angles."""
        elbow = angles.get(f'{self.racket_side}_elbow')
        shoulder = angles.get(f'{self.racket_side}_shoulder')

        if elbow is None or shoulder is None:
            return SwingPhase.READY

        # Simple phase detection heuristics
        if torso_rotation and torso_rotation > 30:
            if shoulder < 60:
                return SwingPhase.BACKSWING
            elif shoulder < 120:
                return SwingPhase.FORWARD_SWING
            else:
                return SwingPhase.FOLLOW_THROUGH
        elif shoulder > 150:
            return SwingPhase.FOLLOW_THROUGH
        elif shoulder > 90:
            return SwingPhase.CONTACT
        else:
            return SwingPhase.PREPARATION

    def _detect_phase_boundaries(
        self,
        metrics: List[SwingMetrics]
    ) -> Dict[SwingPhase, Tuple[int, int]]:
        """Detect start/end frames for each phase."""
        phases = {}
        current_phase = None
        phase_start = 0

        for i, m in enumerate(metrics):
            if m.phase != current_phase:
                if current_phase is not None:
                    phases[current_phase] = (phase_start, i - 1)
                current_phase = m.phase
                phase_start = i

        if current_phase is not None:
            phases[current_phase] = (phase_start, len(metrics) - 1)

        return phases

    def _find_contact_frame(
        self,
        metrics: List[SwingMetrics],
        phases: Dict[SwingPhase, Tuple[int, int]]
    ) -> Optional[int]:
        """Find the contact frame."""
        if SwingPhase.CONTACT in phases:
            start, end = phases[SwingPhase.CONTACT]
            return (start + end) // 2
        return None

    def _analyze_issues(
        self,
        metrics: List[SwingMetrics],
        phases: Dict[SwingPhase, Tuple[int, int]]
    ) -> Tuple[List[str], List[str]]:
        """Analyze swing issues and generate recommendations."""
        issues = []
        recommendations = []

        # Check elbow angle at contact
        contact_idx = self._find_contact_frame(metrics, phases)
        if contact_idx:
            contact = metrics[contact_idx]

            if contact.racket_elbow_angle:
                if contact.racket_elbow_angle < 140:
                    issues.append("Elbow too bent at contact")
                    recommendations.append("Extend your arm more through contact")
                elif contact.racket_elbow_angle > 175:
                    issues.append("Arm too straight at contact")
                    recommendations.append("Keep a slight bend in your elbow")

            if contact.front_knee_angle:
                if contact.front_knee_angle > 170:
                    issues.append("Not enough knee bend")
                    recommendations.append("Bend your knees more to generate power from the ground up")

        # Check torso rotation
        max_rotation = max((m.torso_rotation or 0) for m in metrics)
        if max_rotation < 30:
            issues.append("Insufficient torso rotation")
            recommendations.append("Rotate your shoulders more during the backswing")

        return issues, recommendations

    def _calculate_score(
        self,
        metrics: List[SwingMetrics],
        phases: Dict[SwingPhase, Tuple[int, int]]
    ) -> float:
        """Calculate overall swing score (0-100)."""
        score = 50  # Base score

        # Check key metrics
        contact_idx = self._find_contact_frame(metrics, phases)
        if contact_idx:
            contact = metrics[contact_idx]

            # Elbow angle (max 15 points)
            if contact.racket_elbow_angle:
                ideal_min, ideal_max = self.IDEAL_RANGES['contact_elbow_angle']
                if ideal_min <= contact.racket_elbow_angle <= ideal_max:
                    score += 15
                else:
                    deviation = min(
                        abs(contact.racket_elbow_angle - ideal_min),
                        abs(contact.racket_elbow_angle - ideal_max)
                    )
                    score += max(0, 15 - deviation * 0.5)

            # Knee angle (max 15 points)
            if contact.front_knee_angle:
                ideal_min, ideal_max = self.IDEAL_RANGES['contact_knee_angle']
                if ideal_min <= contact.front_knee_angle <= ideal_max:
                    score += 15
                else:
                    deviation = min(
                        abs(contact.front_knee_angle - ideal_min),
                        abs(contact.front_knee_angle - ideal_max)
                    )
                    score += max(0, 15 - deviation * 0.5)

        # Torso rotation (max 20 points)
        max_rotation = max((m.torso_rotation or 0) for m in metrics)
        ideal_min, ideal_max = self.IDEAL_RANGES['torso_rotation']
        if ideal_min <= max_rotation <= ideal_max:
            score += 20
        else:
            score += max(0, 20 - abs(max_rotation - (ideal_min + ideal_max) / 2) * 0.3)

        return min(100, max(0, score))

    def _calculate_follow_through(
        self,
        metrics: List[SwingMetrics],
        phases: Dict[SwingPhase, Tuple[int, int]]
    ) -> float:
        """Calculate follow-through completion percentage."""
        if SwingPhase.FOLLOW_THROUGH not in phases:
            return 0.0

        start, end = phases[SwingPhase.FOLLOW_THROUGH]
        follow_frames = end - start + 1
        total_frames = len(metrics)

        # Good follow-through should be at least 20% of swing
        expected_frames = total_frames * 0.2
        return min(100, (follow_frames / expected_frames) * 100)


def generate_feedback(result: SwingAnalysisResult) -> str:
    """Generate human-readable feedback from analysis result."""
    feedback = []

    feedback.append(f"Overall Score: {result.overall_score:.0f}/100")
    feedback.append("")

    if result.issues:
        feedback.append("Areas to Improve:")
        for issue in result.issues:
            feedback.append(f"  - {issue}")
        feedback.append("")

    if result.recommendations:
        feedback.append("Recommendations:")
        for rec in result.recommendations:
            feedback.append(f"  - {rec}")

    return "\n".join(feedback)
