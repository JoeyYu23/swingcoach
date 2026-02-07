import React, { useRef, useState, useEffect } from 'react';
import { Button } from './Button';

interface Props {
  onFileSelect: (file: File) => void;
  videoPreview: string | null;
  onAnalyze: (rotation: number) => void;
  isAnalyzing: boolean;
  onRetake: () => void;
}

const ANALYSIS_STEPS = [
  "Detecting player position...",
  "Analyzing swing trajectory...",
  "Evaluating racket angle...",
  "Checking follow-through...",
  "Generating feedback..."
];

export const RecordingPage: React.FC<Props> = ({
  onFileSelect,
  videoPreview,
  onAnalyze,
  isAnalyzing,
  onRetake
}) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [analysisStep, setAnalysisStep] = useState(0);
  const [rotation, setRotation] = useState(0); // 0, 90, 180, 270

  const rotateVideo = () => {
    setRotation((prev) => (prev + 90) % 360);
  };

  const getRotationStyle = () => {
    if (rotation === 0) return {};
    return {
      transform: `rotate(${rotation}deg)`,
      transformOrigin: 'center center',
    };
  };

  useEffect(() => {
    if (!isAnalyzing) {
      setAnalysisStep(0);
      return;
    }
    const interval = setInterval(() => {
      setAnalysisStep(prev => (prev + 1) % ANALYSIS_STEPS.length);
    }, 2000);
    return () => clearInterval(interval);
  }, [isAnalyzing]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      onFileSelect(e.target.files[0]);
    }
  };

  const triggerFileSelect = () => {
    fileInputRef.current?.click();
  };

  return (
    <div className="h-full flex flex-col p-6 bg-court-dark text-white">
      {/* Header */}
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-xl font-bold">Record Swing</h2>
        <div className="text-xs font-mono text-tennis border border-tennis px-2 py-1 rounded">
          SIDE VIEW ONLY
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col justify-center items-center relative rounded-2xl overflow-hidden bg-gray-900 border-2 border-dashed border-gray-700">
        
        {videoPreview ? (
          <div className="relative w-full h-full overflow-hidden">
            <video
              src={videoPreview}
              className={`w-full h-full object-contain ${isAnalyzing ? 'opacity-60' : ''}`}
              style={getRotationStyle()}
              controls={!isAnalyzing}
              playsInline
              autoPlay
              loop
              muted
            />
            {/* Rotate Button */}
            {!isAnalyzing && (
              <button
                onClick={rotateVideo}
                className="absolute top-4 right-4 bg-gray-900/80 text-white rounded-lg p-3 z-20 hover:bg-gray-800"
                title="Rotate Video"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
              </button>
            )}
            {isAnalyzing && (
              <>
                {/* Scan line animation */}
                <div className="absolute inset-0 overflow-hidden pointer-events-none">
                  <div className="absolute w-full h-1 bg-gradient-to-r from-transparent via-tennis to-transparent animate-scan-line" />
                </div>
                {/* Pulsing overlay */}
                <div className="absolute inset-0 bg-tennis/5 animate-pulse pointer-events-none" />
                {/* Analysis status */}
                <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                  <div className="bg-black/70 backdrop-blur-sm rounded-2xl px-6 py-4 text-center">
                    <div className="flex items-center justify-center gap-2 mb-3">
                      <div className="w-2 h-2 bg-tennis rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                      <div className="w-2 h-2 bg-tennis rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                      <div className="w-2 h-2 bg-tennis rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                    </div>
                    <p className="text-tennis font-semibold text-lg">{ANALYSIS_STEPS[analysisStep]}</p>
                  </div>
                </div>
                {/* Corner brackets */}
                <div className="absolute top-4 left-4 w-8 h-8 border-l-2 border-t-2 border-tennis pointer-events-none" />
                <div className="absolute top-4 right-4 w-8 h-8 border-r-2 border-t-2 border-tennis pointer-events-none" />
                <div className="absolute bottom-4 left-4 w-8 h-8 border-l-2 border-b-2 border-tennis pointer-events-none" />
                <div className="absolute bottom-4 right-4 w-8 h-8 border-r-2 border-b-2 border-tennis pointer-events-none" />
              </>
            )}
          </div>
        ) : (
          <div className="text-center p-6 space-y-4" onClick={triggerFileSelect}>
            <div className="w-20 h-20 rounded-full bg-gray-800 flex items-center justify-center mx-auto mb-4">
              <svg className="w-10 h-10 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
            </div>
            <h3 className="text-xl font-bold">Tap to Record</h3>
            <ul className="text-sm text-gray-400 text-left space-y-2 max-w-[200px] mx-auto list-disc pl-4">
              <li>Side profile view</li>
              <li>One forehand swing</li>
              <li>Good lighting</li>
            </ul>
          </div>
        )}

        {/* Hidden Input */}
        <input 
          type="file" 
          accept="video/*" 
          capture="environment" // Forces camera on mobile
          className="hidden" 
          ref={fileInputRef}
          onChange={handleFileChange}
        />
      </div>

      {/* Action Bar */}
      <div className="mt-6 space-y-3">
        {videoPreview ? (
          <>
            <Button onClick={() => onAnalyze(rotation)} fullWidth isLoading={isAnalyzing}>
              ✨ Analyze Swing
            </Button>
            {rotation !== 0 && (
              <p className="text-center text-xs text-gray-500">Video rotated {rotation}°</p>
            )}
            <button
              onClick={() => { setRotation(0); onRetake(); }}
              className="w-full text-gray-400 text-sm py-2 underline"
              disabled={isAnalyzing}
            >
              Retake Video
            </button>
          </>
        ) : (
          <Button onClick={triggerFileSelect} fullWidth>
            Open Camera
          </Button>
        )}
      </div>
    </div>
  );
};