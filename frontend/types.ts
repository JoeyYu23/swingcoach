export type ViewState = 'landing' | 'recording' | 'analyzing' | 'results';

export interface AnalysisResult {
  feedback_text: string; // The core one-liner
  metric_name: string;   // e.g., "Wrist Snap"
  metric_value: string;  // e.g., "Too Late"
  audio_base64?: string; // For playing the voice
}

export interface AppState {
  view: ViewState;
  videoFile: File | null;
  videoPreviewUrl: string | null;
  result: AnalysisResult | null;
  error: string | null;
}