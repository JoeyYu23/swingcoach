import React, { useEffect, useRef, useState } from 'react';
import { AnalysisResult } from '../types';
import { Button } from './Button';

interface Props {
  result: AnalysisResult;
  videoPreview: string | null;
  onReset: () => void;
}

const getScoreFromMetric = (metricValue: string): number => {
  const lower = metricValue.toLowerCase();
  if (lower.includes('good') || lower.includes('excellent') || lower.includes('perfect')) return 90;
  if (lower.includes('slight') || lower.includes('minor')) return 75;
  if (lower.includes('late') || lower.includes('early') || lower.includes('close')) return 60;
  if (lower.includes('too') || lower.includes('very')) return 45;
  return 65;
};

const getScoreColor = (score: number): string => {
  if (score >= 80) return '#22C55E';
  if (score >= 60) return '#CCFF00';
  if (score >= 40) return '#F59E0B';
  return '#EF4444';
};

interface ScoreRingProps {
  score: number;
  size?: number;
}

const ScoreRing: React.FC<ScoreRingProps> = ({ score, size = 120 }) => {
  const strokeWidth = 8;
  const radius = (size - strokeWidth) / 2;
  const circumference = radius * 2 * Math.PI;
  const offset = circumference - (score / 100) * circumference;
  const color = getScoreColor(score);

  return (
    <div className="relative" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="transform -rotate-90">
        {/* Background circle */}
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="#374151"
          strokeWidth={strokeWidth}
        />
        {/* Score circle */}
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          className="animate-score-fill"
          style={{ '--score-offset': offset } as React.CSSProperties}
        />
      </svg>
      {/* Score text */}
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-3xl font-bold" style={{ color }}>{score}</span>
        <span className="text-xs text-gray-400 uppercase">Score</span>
      </div>
    </div>
  );
};

export const ResultsPage: React.FC<Props> = ({ result, videoPreview, onReset }) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [showContent, setShowContent] = useState(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const score = getScoreFromMetric(result.metric_value);

  // Initialize audio and reveal animation on mount
  useEffect(() => {
    // Delay content reveal for dramatic effect
    const revealTimer = setTimeout(() => setShowContent(true), 300);

    let audio: HTMLAudioElement | null = null;
    if (result.audio_base64) {
      const audioSrc = `data:audio/mp3;base64,${result.audio_base64}`;
      audio = new Audio(audioSrc);

      audio.onended = () => setIsPlaying(false);
      audio.onplay = () => setIsPlaying(true);

      audioRef.current = audio;

      // Try to auto-play after reveal
      setTimeout(() => {
        audio?.play().catch(() => {});
      }, 800);
    }

    // Cleanup
    return () => {
      clearTimeout(revealTimer);
      if (audio) {
        audio.pause();
        audio.src = '';
      }
    };
  }, [result]);

  const toggleAudio = () => {
    if (!audioRef.current) return;
    
    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
    } else {
      audioRef.current.play();
    }
  };

  return (
    <div className="h-full flex flex-col bg-court-dark text-white overflow-y-auto">
      {/* Video Background/Header (Sticky) */}
      <div className="relative w-full h-64 bg-black flex-shrink-0">
        {videoPreview && (
          <video 
            src={videoPreview} 
            className="w-full h-full object-cover opacity-60" 
            autoPlay 
            loop 
            muted 
            playsInline
          />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-court-dark via-transparent to-transparent"></div>
        
        {/* Audio Control Floating */}
        <button 
          onClick={toggleAudio}
          className="absolute bottom-4 right-4 bg-tennis text-court-dark rounded-full p-4 shadow-xl z-20 animate-bounce"
        >
          {isPlaying ? (
            <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
          ) : (
             <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
          )}
        </button>
      </div>

      {/* Content */}
      <div className={`px-6 py-2 flex-1 flex flex-col transition-all duration-500 ${showContent ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'}`}>
        {/* Score Ring */}
        <div className="flex justify-center mb-6 -mt-16 relative z-10">
          <div className="bg-gray-900 rounded-full p-3 shadow-2xl border border-gray-700">
            <ScoreRing score={score} size={130} />
          </div>
        </div>

        {/* The Moment: Feedback */}
        <div className="mb-6 text-center">
          <p className="text-tennis text-sm font-bold tracking-widest uppercase mb-3">Coach Says</p>
          <h1 className="text-2xl font-bold leading-tight">
            "{result.feedback_text}"
          </h1>
        </div>

        {/* Metric Card */}
        <div className="bg-gradient-to-br from-gray-800 to-gray-900 rounded-xl p-5 border border-gray-700 mb-6 shadow-lg">
          <div className="flex justify-between items-center">
            <div>
              <span className="text-gray-400 text-xs uppercase block mb-1">Focus Area</span>
              <h3 className="text-xl font-semibold text-white">{result.metric_name}</h3>
            </div>
            <div className="text-right">
              <span className="text-gray-400 text-xs uppercase block mb-1">Status</span>
              <span className="text-lg font-mono text-tennis">{result.metric_value}</span>
            </div>
          </div>
        </div>

        {/* Pro Tip */}
        <div className="bg-tennis/10 border border-tennis/30 rounded-lg p-4 mb-6">
          <p className="text-sm text-tennis font-medium flex items-center gap-2">
            <span>💡</span>
            <span>Practice this 10 times slowly, then at full speed.</span>
          </p>
        </div>

        <div className="mt-auto mb-6">
          <Button onClick={onReset} variant="secondary" fullWidth>
            Analyze Another Swing
          </Button>
        </div>
      </div>
    </div>
  );
};