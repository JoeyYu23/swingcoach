import { PoseAnalysisResult } from "../types";

const API_BASE = "http://localhost:8001";

/**
 * Analyze video using the pose estimation backend.
 */
export const analyzePose = async (file: File, rotation: number = 0): Promise<PoseAnalysisResult> => {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("rotation", rotation.toString());

  const response = await fetch(`${API_BASE}/api/analyze-video`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: "Unknown error" }));
    throw new Error(error.detail || "Failed to analyze video");
  }

  return response.json();
};

/**
 * Check if the pose backend is available.
 */
export const checkBackendHealth = async (): Promise<boolean> => {
  try {
    const response = await fetch(`${API_BASE}/health`);
    return response.ok;
  } catch {
    return false;
  }
};

/**
 * Get annotated video with skeleton overlay and angle labels.
 * Returns a blob URL that can be used as video src.
 */
export const getAnnotatedVideo = async (file: File, rotation: number = 0): Promise<string> => {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("rotation", rotation.toString());

  const response = await fetch(`${API_BASE}/api/annotate-video`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error("Failed to generate annotated video");
  }

  const blob = await response.blob();
  return URL.createObjectURL(blob);
};
