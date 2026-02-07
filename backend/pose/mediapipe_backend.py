"""MediaPipe pose estimation backend implementation using Tasks API (0.10+)."""

from typing import List, Optional
import numpy as np
import cv2
import urllib.request
import os

try:
    import mediapipe as mp
    from mediapipe.tasks import python
    from mediapipe.tasks.python import vision
    MEDIAPIPE_AVAILABLE = True
except ImportError:
    MEDIAPIPE_AVAILABLE = False

from .pose_estimator import PoseEstimator, PoseResult, Keypoint


class MediaPipeBackend(PoseEstimator):
    """MediaPipe pose estimation backend with 33 keypoints.

    Supports both 2D and 3D pose estimation with world landmarks.
    Native support for Apple Silicon and Intel processors (MediaPipe 0.10+).
    Uses the new Tasks API for MediaPipe 0.10+.
    """

    # MediaPipe Pose landmark names (33 keypoints)
    LANDMARK_NAMES = [
        'nose',
        'left_eye_inner', 'left_eye', 'left_eye_outer',
        'right_eye_inner', 'right_eye', 'right_eye_outer',
        'left_ear', 'right_ear',
        'mouth_left', 'mouth_right',
        'left_shoulder', 'right_shoulder',
        'left_elbow', 'right_elbow',
        'left_wrist', 'right_wrist',
        'left_pinky', 'right_pinky',
        'left_index', 'right_index',
        'left_thumb', 'right_thumb',
        'left_hip', 'right_hip',
        'left_knee', 'right_knee',
        'left_ankle', 'right_ankle',
        'left_heel', 'right_heel',
        'left_foot_index', 'right_foot_index',
    ]

    # Model URL
    MODEL_URL = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task"

    def __init__(
        self,
        model_complexity: int = 1,
        min_detection_confidence: float = 0.5,
        min_tracking_confidence: float = 0.5,
        enable_segmentation: bool = False,
        smooth_landmarks: bool = True,
        static_image_mode: bool = False,
        model_path: Optional[str] = None,
    ):
        """Initialize MediaPipe backend.

        Args:
            model_complexity: Model complexity (0=lite, 1=full, 2=heavy)
            min_detection_confidence: Minimum confidence for detection
            min_tracking_confidence: Minimum confidence for tracking
            enable_segmentation: Enable person segmentation mask
            smooth_landmarks: Apply temporal smoothing (not used in Tasks API)
            static_image_mode: Treat each frame independently (VIDEO vs IMAGE mode)
            model_path: Path to model file (will download if not provided)
        """
        if not MEDIAPIPE_AVAILABLE:
            raise ImportError(
                "MediaPipe is not installed. "
                "Install with: pip install mediapipe>=0.10.0"
            )

        super().__init__(
            model_complexity=model_complexity,
            min_detection_confidence=min_detection_confidence,
            min_tracking_confidence=min_tracking_confidence,
            enable_segmentation=enable_segmentation,
            smooth_landmarks=smooth_landmarks,
            static_image_mode=static_image_mode,
            model_path=model_path,
        )

        self.landmarker = None
        self.last_result = None

    def _get_model_path(self) -> str:
        """Get or download model file.

        Returns:
            Path to model file
        """
        model_path = self.config.get('model_path')

        if model_path and os.path.exists(model_path):
            return model_path

        # Default model directory
        model_dir = os.path.join(os.path.dirname(__file__), '..', '..', 'models')
        os.makedirs(model_dir, exist_ok=True)

        # Model file name based on complexity
        complexity = self.config.get('model_complexity', 1)
        model_names = {
            0: 'pose_landmarker_lite.task',
            1: 'pose_landmarker_full.task',
            2: 'pose_landmarker_heavy.task',
        }

        model_file = os.path.join(model_dir, model_names.get(complexity, 'pose_landmarker_full.task'))

        # Download if not exists
        if not os.path.exists(model_file):
            print(f"Downloading model to {model_file}...")
            # Adjust URL based on complexity
            urls = {
                0: "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task",
                1: "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task",
                2: "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task",
            }
            url = urls.get(complexity, urls[1])

            try:
                urllib.request.urlretrieve(url, model_file)
                print("Model downloaded successfully!")
            except Exception as e:
                raise RuntimeError(f"Failed to download model: {e}")

        return model_file

    def initialize(self) -> bool:
        """Initialize MediaPipe Pose Landmarker.

        Returns:
            True if successful
        """
        try:
            model_path = self._get_model_path()

            # Create base options
            base_options = python.BaseOptions(model_asset_path=model_path)

            # Determine running mode
            running_mode = vision.RunningMode.IMAGE if self.config.get('static_image_mode') else vision.RunningMode.VIDEO

            # Create options
            options = vision.PoseLandmarkerOptions(
                base_options=base_options,
                running_mode=running_mode,
                num_poses=1,
                min_pose_detection_confidence=self.config.get('min_detection_confidence', 0.5),
                min_pose_presence_confidence=self.config.get('min_detection_confidence', 0.5),
                min_tracking_confidence=self.config.get('min_tracking_confidence', 0.5),
                output_segmentation_masks=self.config.get('enable_segmentation', False),
            )

            # Create landmarker
            self.landmarker = vision.PoseLandmarker.create_from_options(options)
            self.is_initialized = True
            return True

        except Exception as e:
            print(f"Failed to initialize MediaPipe: {e}")
            return False

    def process_frame(self, frame: np.ndarray) -> Optional[PoseResult]:
        """Process frame and detect pose.

        Args:
            frame: Input image (BGR format)

        Returns:
            PoseResult or None
        """
        if not self.is_initialized or self.landmarker is None:
            return None

        try:
            # Convert BGR to RGB
            frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

            # Create MediaPipe Image
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)

            # Process based on mode
            if self.config.get('static_image_mode'):
                detection_result = self.landmarker.detect(mp_image)
            else:
                # For VIDEO mode, we need to provide timestamp in milliseconds
                import time
                timestamp_ms = int(time.time() * 1000)
                detection_result = self.landmarker.detect_for_video(mp_image, timestamp_ms)

            # Check if pose detected
            if not detection_result.pose_landmarks or len(detection_result.pose_landmarks) == 0:
                return None

            # Extract first pose (we only detect one person)
            pose_landmarks = detection_result.pose_landmarks[0]
            pose_world_landmarks = detection_result.pose_world_landmarks[0] if detection_result.pose_world_landmarks else None

            # Convert to our keypoint format
            keypoints = []
            height, width = frame.shape[:2]

            for idx, landmark in enumerate(pose_landmarks):
                name = self.LANDMARK_NAMES[idx]

                # Get world landmarks if available
                world_x, world_y, world_z = None, None, None
                if pose_world_landmarks:
                    world_lm = pose_world_landmarks[idx]
                    world_x = world_lm.x
                    world_y = world_lm.y
                    world_z = world_lm.z

                keypoint = Keypoint(
                    name=name,
                    x=landmark.x,
                    y=landmark.y,
                    z=landmark.z,
                    visibility=landmark.visibility,
                    presence=getattr(landmark, 'presence', 1.0),
                    world_x=world_x,
                    world_y=world_y,
                    world_z=world_z,
                )
                keypoints.append(keypoint)

            # Calculate overall confidence
            avg_visibility = np.mean([kp.visibility for kp in keypoints])

            return PoseResult(
                keypoints=keypoints,
                confidence=avg_visibility,
                image_width=width,
                image_height=height,
            )

        except Exception as e:
            print(f"Error processing frame: {e}")
            return None

    def draw_landmarks(
        self,
        frame: np.ndarray,
        pose_result: PoseResult,
        draw_connections: bool = True,
    ) -> np.ndarray:
        """Draw pose landmarks on frame.

        Args:
            frame: Input image
            pose_result: Pose detection result
            draw_connections: Draw skeleton connections

        Returns:
            Annotated image
        """
        annotated = frame.copy()

        if not pose_result or not pose_result.keypoints:
            return annotated

        height, width = frame.shape[:2]

        # Draw keypoints
        for keypoint in pose_result.keypoints:
            if keypoint.visibility > 0.5:
                x, y = keypoint.to_image_coords(width, height)
                cv2.circle(annotated, (x, y), 5, (0, 255, 0), -1)

        # Draw connections
        if draw_connections:
            connections = [
                # Torso
                ('left_shoulder', 'right_shoulder'),
                ('left_hip', 'right_hip'),
                ('left_shoulder', 'left_hip'),
                ('right_shoulder', 'right_hip'),
                # Left arm
                ('left_shoulder', 'left_elbow'),
                ('left_elbow', 'left_wrist'),
                ('left_wrist', 'left_thumb'),
                ('left_wrist', 'left_index'),
                ('left_wrist', 'left_pinky'),
                # Right arm
                ('right_shoulder', 'right_elbow'),
                ('right_elbow', 'right_wrist'),
                ('right_wrist', 'right_thumb'),
                ('right_wrist', 'right_index'),
                ('right_wrist', 'right_pinky'),
                # Left leg
                ('left_hip', 'left_knee'),
                ('left_knee', 'left_ankle'),
                ('left_ankle', 'left_heel'),
                ('left_ankle', 'left_foot_index'),
                # Right leg
                ('right_hip', 'right_knee'),
                ('right_knee', 'right_ankle'),
                ('right_ankle', 'right_heel'),
                ('right_ankle', 'right_foot_index'),
                # Face
                ('nose', 'left_eye'),
                ('nose', 'right_eye'),
                ('left_eye', 'left_ear'),
                ('right_eye', 'right_ear'),
                ('mouth_left', 'mouth_right'),
            ]

            for start_name, end_name in connections:
                start_kp = pose_result.get_keypoint(start_name)
                end_kp = pose_result.get_keypoint(end_name)

                if (start_kp and end_kp and
                    start_kp.visibility > 0.5 and end_kp.visibility > 0.5):
                    start_pos = start_kp.to_image_coords(width, height)
                    end_pos = end_kp.to_image_coords(width, height)
                    cv2.line(annotated, start_pos, end_pos, (0, 255, 255), 2)

        return annotated

    def release(self):
        """Release MediaPipe resources."""
        if self.landmarker is not None:
            self.landmarker.close()
            self.landmarker = None
        self.is_initialized = False

    def get_keypoint_names(self) -> List[str]:
        """Get list of keypoint names.

        Returns:
            List of 33 landmark names
        """
        return self.LANDMARK_NAMES.copy()

    @property
    def backend_name(self) -> str:
        """Get backend name.

        Returns:
            'MediaPipe'
        """
        return "MediaPipe"

    @staticmethod
    def is_available() -> bool:
        """Check if MediaPipe is available.

        Returns:
            True if MediaPipe is installed
        """
        return MEDIAPIPE_AVAILABLE
