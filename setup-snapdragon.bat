@echo off
REM SwingCoach - Snapdragon X Elite Setup Script
REM For Windows ARM64 devices with Qualcomm Oryon CPU

echo === SwingCoach Snapdragon Setup ===

REM Check Python
python --version >nul 2>&1
if errorlevel 1 (
    echo Python not found. Please install Python ARM64 from python.org
    echo Or run: winget install Python.Python.3.11 --architecture arm64
    pause
    exit /b 1
)

echo Python found:
python --version

REM Navigate to backend
cd backend

REM Create virtual environment
echo.
echo Creating virtual environment...
python -m venv venv

REM Activate venv
echo Activating virtual environment...
call venv\Scripts\activate.bat

REM Upgrade pip
echo.
echo Upgrading pip...
python -m pip install --upgrade pip

REM Install dependencies
echo.
echo Installing dependencies...
pip install -r requirements.txt

REM Verify
echo.
echo Verifying installation...
python -c "import mediapipe; print(f'MediaPipe: {mediapipe.__version__}')"
python -c "import cv2; print(f'OpenCV: {cv2.__version__}')"

echo.
echo === Setup Complete ===
echo.
echo To run the backend:
echo   cd backend
echo   venv\Scripts\activate.bat
echo   python app.py
echo.
echo Backend will run on http://localhost:8001
pause
