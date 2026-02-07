"""
SwingCoach Backend API
FastAPI server for tennis swing analysis.
"""

import asyncio
import logging
import os
import tempfile
import time

from typing import List, Optional
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel

from dotenv import load_dotenv

from vision_provider import analyze_video, generate_tts, get_provider_name
from network_info import get_local_ip, get_connection_url

logger = logging.getLogger(__name__)

# Load .env file (for GEMINI_API_KEY etc.)
load_dotenv()

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

# Global instances (types resolved at runtime in startup)
pose_backend = None
analyzer = None

PORT = 8001


class FrameData(BaseModel):
    """Data for a single frame."""
    frame_index: int
    phase: str
    # Arm angles
    left_elbow: Optional[float] = None
    right_elbow: Optional[float] = None
    left_shoulder: Optional[float] = None
    right_shoulder: Optional[float] = None
    left_wrist: Optional[float] = None
    right_wrist: Optional[float] = None
    # Leg angles
    left_hip: Optional[float] = None
    right_hip: Optional[float] = None
    left_knee: Optional[float] = None
    right_knee: Optional[float] = None
    left_ankle: Optional[float] = None
    right_ankle: Optional[float] = None
    # Posture metrics
    torso_rotation: Optional[float] = None
    shoulder_tilt: Optional[float] = None
    hip_tilt: Optional[float] = None
    body_lean: Optional[float] = None
    head_tilt: Optional[float] = None
    spine_curve: Optional[float] = None


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
    # Summary angles at contact point
    contact_angles: Optional[dict] = None
    # Posture metrics at contact
    contact_posture: Optional[dict] = None
    # Time series data
    frames: List[FrameData]


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


class GeminiResult(BaseModel):
    """Result from Gemini vision analysis."""
    feedback_text: str
    metric_name: str
    metric_value: str


class CompleteAnalysisResponse(BaseModel):
    """Combined pose + Gemini analysis for the phone endpoint."""
    pose_analysis: Optional[AnalysisResponse] = None
    gemini_analysis: Optional[GeminiResult] = None
    audio_base64: Optional[str] = None
    processing_info: Optional[dict] = None


@app.on_event("startup")
async def startup():
    """Initialize pose estimation backend on startup."""
    global pose_backend, analyzer

    try:
        from pose import MediaPipeBackend
        from analysis import TennisSwingAnalyzer

        pose_backend = MediaPipeBackend(
            model_complexity=1,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5,
        )

        if not pose_backend.initialize():
            logger.warning("MediaPipe failed to initialize — pose analysis disabled")
            pose_backend = None
        else:
            analyzer = TennisSwingAnalyzer(handedness='right')
    except Exception as e:
        logger.warning("MediaPipe unavailable (%s) — pose analysis disabled", e)
        pose_backend = None
        analyzer = None

    provider = get_provider_name()
    ip = get_local_ip()
    url = get_connection_url(PORT)
    print(f"SwingCoach API ready!")
    print(f"  Vision provider: {provider}")
    print(f"  Pose estimation: {'available' if pose_backend else 'unavailable'}")
    print(f"  LAN IP:  {ip}")
    print(f"  Phone connect URL: {url}")
    print(f"  Endpoints:")
    print(f"    POST {url}/api/analyze-video   (pose only)")
    print(f"    POST {url}/api/analyze-swing   (pose + vision + TTS)")
    print(f"    GET  {url}/api/network-info")


@app.on_event("shutdown")
async def shutdown():
    """Cleanup on shutdown."""
    global pose_backend
    if pose_backend:
        pose_backend.release()


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "ok",
        "vision_provider": get_provider_name(),
        "pose": "available" if pose_backend else "unavailable",
    }


def rotate_frame(frame, rotation: int):
    """Rotate frame by given degrees (0, 90, 180, 270)."""
    import cv2
    if rotation == 90:
        return cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
    elif rotation == 180:
        return cv2.rotate(frame, cv2.ROTATE_180)
    elif rotation == 270:
        return cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)
    return frame


def _analyze_video_sync(
    tmp_path: str,
    rotation: int,
    pose_backend_inst,
    analyzer_inst,
) -> AnalysisResponse:
    """Run pose estimation + swing analysis on a video file (CPU-bound, synchronous).

    Extracted so it can be reused by both /api/analyze-video and /api/analyze-swing.
    """
    import cv2
    pose_results = []
    cap = cv2.VideoCapture(tmp_path)

    if not cap.isOpened():
        raise HTTPException(status_code=400, detail="Could not open video file")

    while True:
        ret, frame = cap.read()
        if not ret:
            break
        if rotation != 0:
            frame = rotate_frame(frame, rotation)
        pose_result = pose_backend_inst.process_frame(frame)
        if pose_result and pose_result.is_valid():
            pose_results.append(pose_result)

    cap.release()

    if len(pose_results) < 5:
        raise HTTPException(
            status_code=400,
            detail="Could not detect pose in enough frames. Please ensure the full body is visible."
        )

    result = analyzer_inst.analyze_swing(pose_results)

    # Generate feedback text
    if result.issues:
        feedback = f"{result.issues[0]}. {result.recommendations[0] if result.recommendations else ''}"
    else:
        feedback = "Great swing! Keep up the good work."

    # Build time series data with all angles
    from pose import AngleCalculator
    calc = AngleCalculator(use_3d=True)
    frames_data = []
    contact_angles = None
    contact_posture = None

    for i, (pose, metrics) in enumerate(zip(pose_results, result.metrics_per_frame)):
        angles = calc.calculate_all_angles(pose)
        posture = calc.calculate_posture_metrics(pose)

        frame_data = FrameData(
            frame_index=metrics.frame_index,
            phase=metrics.phase.value,
            left_elbow=angles.get('left_elbow'),
            right_elbow=angles.get('right_elbow'),
            left_shoulder=angles.get('left_shoulder'),
            right_shoulder=angles.get('right_shoulder'),
            left_wrist=angles.get('left_wrist'),
            right_wrist=angles.get('right_wrist'),
            left_hip=angles.get('left_hip'),
            right_hip=angles.get('right_hip'),
            left_knee=angles.get('left_knee'),
            right_knee=angles.get('right_knee'),
            left_ankle=angles.get('left_ankle'),
            right_ankle=angles.get('right_ankle'),
            torso_rotation=metrics.torso_rotation,
            shoulder_tilt=posture.get('shoulder_tilt'),
            hip_tilt=posture.get('hip_tilt'),
            body_lean=posture.get('body_lean'),
            head_tilt=posture.get('head_tilt'),
            spine_curve=posture.get('spine_curve'),
        )
        frames_data.append(frame_data)

        if metrics.phase.value == 'contact' and contact_angles is None:
            contact_angles = angles
            contact_posture = posture

    # Fallback: if no contact phase found, use middle frame
    if contact_angles is None and len(frames_data) > 0:
        mid_idx = len(pose_results) // 2
        mid_pose = pose_results[mid_idx]
        contact_angles = calc.calculate_all_angles(mid_pose)
        contact_posture = calc.calculate_posture_metrics(mid_pose)

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
        contact_angles=contact_angles,
        contact_posture=contact_posture,
        frames=frames_data,
    )


@app.post("/api/analyze-video", response_model=AnalysisResponse)
async def analyze_video_endpoint(file: UploadFile = File(...), rotation: int = 0):
    """
    Analyze a tennis swing video (pose estimation only).

    Args:
        file: Video file (mp4, mov, etc.)
        rotation: Rotation angle (0, 90, 180, 270)

    Returns:
        Complete swing analysis with scores and recommendations
    """
    global pose_backend, analyzer

    if not pose_backend or not analyzer:
        raise HTTPException(status_code=503, detail="Pose estimation unavailable")

    suffix = os.path.splitext(file.filename)[1] or ".mp4"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(
            None, _analyze_video_sync, tmp_path, rotation, pose_backend, analyzer
        )
    finally:
        os.unlink(tmp_path)


@app.post("/api/analyze-swing", response_model=CompleteAnalysisResponse)
async def analyze_swing(
    file: UploadFile = File(...),
    rotation: int = Form(0),
):
    """
    Combined analysis: MediaPipe pose (edge) + vision (cloud/local) + TTS.

    Runs pose estimation and vision analysis in parallel, then TTS sequentially.
    Designed for phone -> PC -> phone pipeline.

    Args:
        file: Video file (mp4, mov, etc.)
        rotation: Rotation angle (0, 90, 180, 270)

    Returns:
        Combined pose analysis, vision feedback, TTS audio, and timing metadata
    """
    global pose_backend, analyzer

    # Save uploaded file
    suffix = os.path.splitext(file.filename or "video.mp4")[1] or ".mp4"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        video_bytes = await file.read()
        tmp.write(video_bytes)
        tmp_path = tmp.name

    try:
        overall_start = time.time()
        loop = asyncio.get_event_loop()

        pose_result: Optional[AnalysisResponse] = None
        vision_result = None
        pose_error = None
        vision_error = None

        # --- Run pose (if available) and vision in parallel ---
        if pose_backend and analyzer:
            pose_coro = loop.run_in_executor(
                None, _analyze_video_sync, tmp_path, rotation, pose_backend, analyzer
            )
            results = await asyncio.gather(
                pose_coro,
                analyze_video(video_bytes, "video/mp4"),
                return_exceptions=True,
            )
            if isinstance(results[0], Exception):
                pose_error = str(results[0])
            else:
                pose_result = results[0]
            if isinstance(results[1], Exception):
                vision_error = str(results[1])
            else:
                vision_result = results[1]
        else:
            pose_error = "MediaPipe unavailable — pose analysis skipped"
            try:
                vision_result = await analyze_video(video_bytes, "video/mp4")
            except Exception as e:
                vision_error = str(e)

        parallel_done = time.time()

        # --- TTS (sequential, only if vision succeeded) ---
        audio_base64 = None
        if vision_result:
            audio_base64 = await generate_tts(vision_result.feedback_text)

        total_elapsed = time.time() - overall_start

        return CompleteAnalysisResponse(
            pose_analysis=pose_result,
            gemini_analysis=GeminiResult(**vision_result.to_dict()) if vision_result else None,
            audio_base64=audio_base64,
            processing_info={
                "total_seconds": round(total_elapsed, 2),
                "parallel_seconds": round(parallel_done - overall_start, 2),
                "pose_error": pose_error,
                "vision_error": vision_error,
            },
        )

    finally:
        os.unlink(tmp_path)


@app.get("/api/network-info")
async def network_info():
    """Return LAN IP and connection info for phone pairing."""
    ip = get_local_ip()
    url = get_connection_url(PORT)
    return {
        "ip": ip,
        "url": url,
        "endpoints": {
            "analyze_video": f"{url}/api/analyze-video",
            "analyze_swing": f"{url}/api/analyze-swing",
            "health": f"{url}/health",
        },
    }


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
        raise HTTPException(status_code=503, detail="Pose estimation unavailable")

    import cv2
    import numpy as np
    content = await file.read()
    nparr = np.frombuffer(content, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    if frame is None:
        raise HTTPException(status_code=400, detail="Could not decode image")

    pose_result = pose_backend.process_frame(frame)

    if not pose_result or not pose_result.is_valid():
        raise HTTPException(status_code=400, detail="Could not detect pose in image")

    from pose import AngleCalculator
    calc = AngleCalculator(use_3d=True)
    angles = calc.calculate_all_angles(pose_result)
    metrics = calc.calculate_posture_metrics(pose_result)

    return {
        "angles": angles,
        "posture_metrics": metrics,
        "confidence": pose_result.confidence,
    }


@app.post("/api/annotate-video")
async def annotate_video(file: UploadFile = File(...), rotation: int = 0):
    """
    Process video and return annotated version with skeleton overlay.

    Args:
        file: Video file
        rotation: Rotation angle (0, 90, 180, 270)

    Returns:
        Annotated video file with skeleton and angles
    """
    global pose_backend

    if not pose_backend:
        raise HTTPException(status_code=503, detail="Pose estimation unavailable")

    import cv2

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

        # Adjust dimensions if rotating 90 or 270 degrees
        if rotation in [90, 270]:
            width, height = height, width

        # Use H.264 codec for browser compatibility
        fourcc = cv2.VideoWriter_fourcc(*'avc1')
        out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))

        from pose import AngleCalculator
        calc = AngleCalculator(use_3d=True)

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            # Apply rotation if specified
            if rotation != 0:
                frame = rotate_frame(frame, rotation)

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
    uvicorn.run(app, host="0.0.0.0", port=PORT)
