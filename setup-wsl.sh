#!/bin/bash
# SwingCoach - WSL Setup Script
# For Windows Subsystem for Linux (Ubuntu/Debian)

set -e

echo "=== SwingCoach WSL Setup ==="

# Check if running in WSL
if ! grep -q microsoft /proc/version 2>/dev/null; then
    echo "Warning: Not running in WSL environment"
fi

# Install system dependencies
echo ""
echo "Installing system dependencies..."
sudo apt-get update
sudo apt-get install -y python3 python3-pip python3-venv
sudo apt-get install -y libgl1-mesa-glx libglib2.0-0  # OpenCV dependencies

# Navigate to backend
cd backend

# Create virtual environment
echo ""
echo "Creating virtual environment..."
python3 -m venv venv

# Activate venv
echo "Activating virtual environment..."
source venv/bin/activate

# Upgrade pip
echo ""
echo "Upgrading pip..."
pip install --upgrade pip

# Install dependencies
echo ""
echo "Installing Python dependencies..."
pip install -r requirements.txt

# Verify installation
echo ""
echo "Verifying installation..."
python -c "import mediapipe; print(f'MediaPipe: {mediapipe.__version__}')"
python -c "import cv2; print(f'OpenCV: {cv2.__version__}')"

echo ""
echo "=== Setup Complete ==="
echo ""
echo "To run the backend:"
echo "  cd backend"
echo "  source venv/bin/activate"
echo "  python app.py"
echo ""
echo "Backend will run on http://localhost:8001"
echo "Access from Windows browser at the same URL"
