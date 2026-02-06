# SwingCoach - Snapdragon X Elite Setup Script
# For Windows ARM64 devices with Qualcomm Oryon CPU

Write-Host "=== SwingCoach Snapdragon Setup ===" -ForegroundColor Cyan

# Check if running on ARM64
$arch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture
if ($arch -ne "Arm64") {
    Write-Host "Warning: This script is optimized for ARM64 (Snapdragon). Current: $arch" -ForegroundColor Yellow
}

# Check Python
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    Write-Host "Python not found. Installing via winget..." -ForegroundColor Yellow
    winget install Python.Python.3.11 --architecture arm64 --accept-package-agreements
    # Refresh PATH
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
}

# Verify Python version
Write-Host "`nPython version:" -ForegroundColor Green
python --version

# Create virtual environment
Write-Host "`nCreating virtual environment..." -ForegroundColor Green
Set-Location backend
python -m venv venv

# Activate venv
Write-Host "Activating virtual environment..." -ForegroundColor Green
.\venv\Scripts\Activate.ps1

# Upgrade pip
Write-Host "`nUpgrading pip..." -ForegroundColor Green
python -m pip install --upgrade pip

# Install dependencies
Write-Host "`nInstalling dependencies..." -ForegroundColor Green
pip install -r requirements.txt

# Verify MediaPipe installation
Write-Host "`nVerifying MediaPipe..." -ForegroundColor Green
python -c "import mediapipe; print(f'MediaPipe version: {mediapipe.__version__}')"

Write-Host "`n=== Setup Complete ===" -ForegroundColor Cyan
Write-Host "To run the backend:" -ForegroundColor White
Write-Host "  cd backend" -ForegroundColor Yellow
Write-Host "  .\venv\Scripts\Activate.ps1" -ForegroundColor Yellow
Write-Host "  python app.py" -ForegroundColor Yellow
Write-Host "`nBackend will run on http://localhost:8001" -ForegroundColor White
