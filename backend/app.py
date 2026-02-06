"""
SwingCoach Backend API
FastAPI server for tennis swing analysis.
"""

import os
import tempfile
import cv2
import numpy as np
from typing import List, Optional
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel

from pose import MediaPipeBackend, AngleCalculator, PoseResult
from analysis import TennisSwingAnalyzer

app = FastAPI(
    title="SwingCoach API",
    description="AI-powered tennis swing analysis",
    version="1.0.0"
)

# CORS for frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global instances
pose_backend: Optional[MediaPipeBackend] = None
analyzer: Optional[TennisSwingAnalyzer] = None


class AnalysisResponse(BaseModel):
    """Response model for swing analysis."""
    total_frames: int
    overall_score: float
    max_torso_rotation: float
    contact_elbow_angle: float
    contact_knee_angle: float
    follow_through_completion: float
    issues: List[str]
    recommendations: List[str]
    feedback_text: str


class FrameAngles(BaseModel):
    """Angles for a single frame."""
    frame_index: int
    left_elbow: Optional[float]
    right_elbow: Optional[float]
    left_shoulder: Optional[float]
    right_shoulder: Optional[float]
    left_knee: Optional[float]
    right_knee: Optional[float]
    torso_rotation: Optional[float]


@app.on_event("startup")
async def startup():
    """Initialize pose estimation backend on startup."""
    global pose_backend, analyzer

    pose_backend = MediaPipeBackend(
        model_complexity=1,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    if not pose_backend.initialize():
        raise RuntimeError("Failed to initialize pose estimation backend")

    analyzer = TennisSwingAnalyzer(handedness='right')
    print("SwingCoach API ready!")


@app.on_event("shutdown")
async def shutdown():
    """Cleanup on shutdown."""
    global pose_backend
    if pose_backend:
        pose_backend.release()


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "ok", "backend": "mediapipe"}


@app.post("/api/analyze-video", response_model=AnalysisResponse)
async def analyze_video(file: UploadFile = File(...)):
    """
    Analyze a tennis swing video.

    Args:
        file: Video file (mp4, mov, etc.)

    Returns:
        Complete swing analysis with scores and recommendations
    """
    global pose_backend, analyzer

    if not pose_backend or not analyzer:
        raise HTTPException(status_code=500, detail="Backend not initialized")

    # Save uploaded file temporarily
    suffix = os.path.splitext(file.filename)[1] or ".mp4"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        # Process video
        pose_results = []
        cap = cv2.VideoCapture(tmp_path)

        if not cap.isOpened():
            raise HTTPException(status_code=400, detail="Could not open video file")

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            pose_result = pose_backend.process_frame(frame)
            if pose_result and pose_result.is_valid():
                pose_results.append(pose_result)

        cap.release()

        if len(pose_results) < 5:
            raise HTTPException(
                status_code=400,
                detail="Could not detect pose in enough frames. Please ensure the full body is visible."
            )

        # Analyze swing
        result = analyzer.analyze_swing(pose_results)

        # Generate feedback text
        if result.issues:
            feedback = f"{result.issues[0]}. {result.recommendations[0] if result.recommendations else ''}"
        else:
            feedback = "Great swing! Keep up the good work."

        return AnalysisResponse(
            total_frames=result.total_frames,
            overall_score=result.overall_score,
            max_torso_rotation=result.max_torso_rotation,
            contact_elbow_angle=result.contact_elbow_angle,
            contact_knee_angle=result.contact_knee_angle,
            follow_through_completion=result.follow_through_completion,
            issues=result.issues,
            recommendations=result.recommendations,
            feedback_text=feedback,
        )

    finally:
        # Cleanup temp file
        os.unlink(tmp_path)


@app.post("/api/analyze-frame")
async def analyze_frame(file: UploadFile = File(...)):
    """
    Analyze a single frame/image.

    Args:
        file: Image file (jpg, png)

    Returns:
        Joint angles and pose metrics
    """
    global pose_backend

    if not pose_backend:
        raise HTTPException(status_code=500, detail="Backend not initialized")

    content = await file.read()
    nparr = np.frombuffer(content, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    if frame is None:
        raise HTTPException(status_code=400, detail="Could not decode image")

    pose_result = pose_backend.process_frame(frame)

    if not pose_result or not pose_result.is_valid():
        raise HTTPException(status_code=400, detail="Could not detect pose in image")

    calc = AngleCalculator(use_3d=True)
    angles = calc.calculate_all_angles(pose_result)
    metrics = calc.calculate_posture_metrics(pose_result)

    return {
        "angles": angles,
        "posture_metrics": metrics,
        "confidence": pose_result.confidence,
    }


@app.post("/api/annotate-video")
async def annotate_video(file: UploadFile = File(...)):
    """
    Process video and return annotated version with skeleton overlay.

    Args:
        file: Video file

    Returns:
        Annotated video file with skeleton and angles
    """
    global pose_backend

    if not pose_backend:
        raise HTTPException(status_code=500, detail="Backend not initialized")

    # Save uploaded file
    suffix = os.path.splitext(file.filename)[1] or ".mp4"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp_in:
        content = await file.read()
        tmp_in.write(content)
        input_path = tmp_in.name

    output_path = input_path.replace(suffix, f"_annotated{suffix}")

    try:
        cap = cv2.VideoCapture(input_path)
        if not cap.isOpened():
            raise HTTPException(status_code=400, detail="Could not open video")

        fps = cap.get(cv2.CAP_PROP_FPS)
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))

        calc = AngleCalculator(use_3d=True)

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            pose_result = pose_backend.process_frame(frame)

            if pose_result and pose_result.is_valid():
                # Draw skeleton
                frame = pose_backend.draw_landmarks(frame, pose_result)

                # Calculate and display angles
                angles = calc.calculate_all_angles(pose_result)

                # Draw angle info
                y_offset = 30
                for joint, angle in angles.items():
                    if angle is not None:
                        text = f"{joint}: {angle:.1f}"
                        cv2.putText(frame, text, (10, y_offset),
                                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 1)
                        y_offset += 20

            out.write(frame)

        cap.release()
        out.release()

        return FileResponse(
            output_path,
            media_type="video/mp4",
            filename=f"annotated_{file.filename}"
        )

    finally:
        os.unlink(input_path)
        # Note: output file will be cleaned up after response


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
