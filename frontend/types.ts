export type ViewState = 'landing' | 'recording' | 'analyzing' | 'results';

export interface FrameData {
  frame_index: number;
  phase: string;
  // Arm angles
  left_elbow: number | null;
  right_elbow: number | null;
  left_shoulder: number | null;
  right_shoulder: number | null;
  left_wrist: number | null;
  right_wrist: number | null;
  // Leg angles
  left_hip: number | null;
  right_hip: number | null;
  left_knee: number | null;
  right_knee: number | null;
  left_ankle: number | null;
  right_ankle: number | null;
  // Posture metrics
  torso_rotation: number | null;
  shoulder_tilt: number | null;
  hip_tilt: number | null;
  body_lean: number | null;
  head_tilt: number | null;
  spine_curve: number | null;
}

export interface PoseAnalysisResult {
  total_frames: number;
  overall_score: number;
  max_torso_rotation: number;
  contact_elbow_angle: number;
  contact_knee_angle: number;
  follow_through_completion: number;
  issues: string[];
  recommendations: string[];
  feedback_text: string;
  // Summary angles at contact point
  contact_angles?: Record<string, number | null>;
  // Posture metrics at contact
  contact_posture?: Record<string, number | null>;
  frames: FrameData[];
}

export interface AnalysisResult {
  feedback_text: string; // The core one-liner
  metric_name: string;   // e.g., "Wrist Snap"
  metric_value: string;  // e.g., "Too Late"
  audio_base64?: string; // For playing the voice
  pose_analysis?: PoseAnalysisResult; // Pose estimation data
  annotated_video_url?: string; // Video with skeleton overlay
}

export interface AppState {
  view: ViewState;
  videoFile: File | null;
  videoPreviewUrl: string | null;
  result: AnalysisResult | null;
  error: string | null;
}