import React, { useEffect, useRef, useState } from 'react';
import { AnalysisResult, FrameData } from '../types';
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

// Phase colors for background
const PHASE_COLORS: Record<string, string> = {
  ready: '#6B7280',
  preparation: '#3B82F6',
  backswing: '#8B5CF6',
  forward_swing: '#F59E0B',
  contact: '#EF4444',
  follow_through: '#22C55E',
};

// Line colors for different angles
const ANGLE_COLORS = {
  // Arms - warm colors
  left_elbow: '#FF6B6B',
  right_elbow: '#EE5A24',
  left_shoulder: '#F8B500',
  right_shoulder: '#F79F1F',
  left_wrist: '#A3CB38',
  right_wrist: '#009432',
  // Legs - cool colors
  left_hip: '#00CEC9',
  right_hip: '#0984E3',
  left_knee: '#6C5CE7',
  right_knee: '#A55EEA',
  left_ankle: '#FD79A8',
  right_ankle: '#E84393',
  // Posture - neutral/earth tones
  torso_rotation: '#CCFF00',
  shoulder_tilt: '#00D9FF',
  hip_tilt: '#FF9F43',
  body_lean: '#54A0FF',
  head_tilt: '#5F27CD',
  spine_curve: '#C4E538',
};

// Angle group definitions
const ANGLE_GROUPS = {
  arms: {
    title: 'Arm Angles',
    keys: ['left_elbow', 'right_elbow', 'left_shoulder', 'right_shoulder', 'left_wrist', 'right_wrist'],
  },
  legs: {
    title: 'Leg Angles',
    keys: ['left_hip', 'right_hip', 'left_knee', 'right_knee', 'left_ankle', 'right_ankle'],
  },
  posture: {
    title: 'Posture Metrics',
    keys: ['torso_rotation', 'shoulder_tilt', 'hip_tilt', 'body_lean', 'head_tilt', 'spine_curve'],
  },
};

// Multi-line time series chart component
interface MultiAngleChartProps {
  frames: FrameData[];
  title: string;
  angleKeys: string[];
  height?: number;
}

const MultiAngleChart: React.FC<MultiAngleChartProps> = ({ frames, title, angleKeys, height = 160 }) => {
  if (!frames || frames.length === 0) return null;

  const width = 320;
  const padding = { top: 10, right: 10, bottom: 45, left: 35 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;

  // Collect all data for scale calculation
  const allValues: number[] = [];
  angleKeys.forEach(key => {
    frames.forEach(f => {
      const val = (f as any)[key];
      if (val !== null && val !== undefined) {
        allValues.push(val);
      }
    });
  });

  if (allValues.length === 0) return null;

  const minY = Math.min(...allValues) - 10;
  const maxY = Math.max(...allValues) + 10;
  const yScale = (v: number) => chartHeight - ((v - minY) / (maxY - minY)) * chartHeight;
  const xScale = (i: number) => frames.length > 1 ? (i / (frames.length - 1)) * chartWidth : chartWidth / 2;

  // Build path for a single angle
  const buildPath = (key: string) => {
    let path = '';
    let started = false;
    frames.forEach((f, i) => {
      const v = (f as any)[key];
      if (v !== null && v !== undefined) {
        const x = xScale(i);
        const y = yScale(v);
        if (!started) {
          path += `M ${x} ${y}`;
          started = true;
        } else {
          path += ` L ${x} ${y}`;
        }
      }
    });
    return path;
  };

  // Phase backgrounds
  const phaseRects: { phase: string; start: number; end: number }[] = [];
  let currentPhase = frames[0]?.phase;
  let phaseStart = 0;

  for (let i = 1; i <= frames.length; i++) {
    const phase = frames[i]?.phase;
    if (phase !== currentPhase || i === frames.length) {
      phaseRects.push({
        phase: currentPhase,
        start: phaseStart,
        end: i - 1,
      });
      currentPhase = phase;
      phaseStart = i;
    }
  }

  // Filter to only show angles that have data
  const activeKeys = angleKeys.filter(key =>
    frames.some(f => (f as any)[key] !== null && (f as any)[key] !== undefined)
  );

  return (
    <div className="bg-gray-900/50 rounded-lg p-3 mb-3">
      <p className="text-gray-400 text-xs uppercase mb-2 font-bold">{title}</p>
      <svg width={width} height={height} className="w-full" viewBox={`0 0 ${width} ${height}`}>
        <g transform={`translate(${padding.left}, ${padding.top})`}>
          {/* Phase backgrounds */}
          {phaseRects.map((rect, i) => (
            <rect
              key={i}
              x={xScale(rect.start)}
              y={0}
              width={Math.max(xScale(rect.end) - xScale(rect.start) + 2, 0)}
              height={chartHeight}
              fill={PHASE_COLORS[rect.phase] || '#374151'}
              opacity={0.15}
            />
          ))}

          {/* Grid lines */}
          {[0, 0.25, 0.5, 0.75, 1].map((t, i) => {
            const y = t * chartHeight;
            const value = Math.round(maxY - t * (maxY - minY));
            return (
              <g key={i}>
                <line x1={0} y1={y} x2={chartWidth} y2={y} stroke="#374151" strokeDasharray="2,2" />
                <text x={-5} y={y + 4} fill="#6B7280" fontSize={9} textAnchor="end">{value}°</text>
              </g>
            );
          })}

          {/* Angle lines */}
          {activeKeys.map(key => (
            <path
              key={key}
              d={buildPath(key)}
              fill="none"
              stroke={(ANGLE_COLORS as any)[key] || '#888'}
              strokeWidth={1.5}
              opacity={0.9}
            />
          ))}
        </g>
      </svg>

      {/* Legend - compact grid */}
      <div className="flex flex-wrap gap-x-3 gap-y-1 mt-2">
        {activeKeys.map(key => (
          <div key={key} className="flex items-center gap-1">
            <div
              className="w-2 h-2 rounded-full"
              style={{ backgroundColor: (ANGLE_COLORS as any)[key] || '#888' }}
            />
            <span className="text-gray-500 text-xs capitalize">
              {key.replace('_', ' ').replace('left', 'L').replace('right', 'R')}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};

// Full body angle timeline component
interface FullBodyTimelineProps {
  frames: FrameData[];
}

const FullBodyTimeline: React.FC<FullBodyTimelineProps> = ({ frames }) => {
  if (!frames || frames.length === 0) return null;

  return (
    <div className="space-y-2">
      <p className="text-gray-400 text-xs uppercase font-bold mb-3">Full Body Motion Timeline</p>

      {/* Arm angles chart */}
      <MultiAngleChart
        frames={frames}
        title="🦾 Arm Angles"
        angleKeys={ANGLE_GROUPS.arms.keys}
        height={160}
      />

      {/* Leg angles chart */}
      <MultiAngleChart
        frames={frames}
        title="🦵 Leg Angles"
        angleKeys={ANGLE_GROUPS.legs.keys}
        height={160}
      />

      {/* Posture metrics chart */}
      <MultiAngleChart
        frames={frames}
        title="🧍 Posture Metrics"
        angleKeys={ANGLE_GROUPS.posture.keys}
        height={160}
      />

      {/* Phase legend - show once at bottom */}
      <div className="bg-gray-900/30 rounded-lg p-2 mt-2">
        <p className="text-gray-500 text-xs mb-2">Swing Phases:</p>
        <div className="flex flex-wrap gap-2">
          {Object.entries(PHASE_COLORS).map(([phase, color]) => (
            <div key={phase} className="flex items-center gap-1">
              <div className="w-2 h-2 rounded-full" style={{ backgroundColor: color }} />
              <span className="text-gray-500 text-xs capitalize">{phase.replace('_', ' ')}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

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
  const [showAnnotated, setShowAnnotated] = useState(true); // Default to annotated view
  const [videoRotation, setVideoRotation] = useState(0); // 0, 90, 180, 270
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Use pose analysis score if available, otherwise estimate from metric
  const score = Math.round(result.pose_analysis?.overall_score ?? getScoreFromMetric(result.metric_value));
  const poseData = result.pose_analysis;
  const hasAnnotatedVideo = !!result.annotated_video_url;

  const rotateVideo = () => {
    setVideoRotation((prev) => (prev + 90) % 360);
  };

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
      <div className="relative w-full h-72 bg-black flex-shrink-0">
        {/* Video - show annotated or original based on toggle */}
        {(showAnnotated && result.annotated_video_url) ? (
          <video
            key="annotated"
            src={result.annotated_video_url}
            className="w-full h-full object-contain"
            autoPlay
            loop
            muted
            playsInline
            controls
          />
        ) : videoPreview && (
          <video
            key="original"
            src={videoPreview}
            className="w-full h-full object-cover opacity-60"
            autoPlay
            loop
            muted
            playsInline
          />
        )}
        {!showAnnotated && <div className="absolute inset-0 bg-gradient-to-t from-court-dark via-transparent to-transparent"></div>}

        {/* Video Toggle Button */}
        {hasAnnotatedVideo && (
          <button
            onClick={() => setShowAnnotated(!showAnnotated)}
            className="absolute top-4 left-4 bg-gray-900/80 text-white rounded-lg px-3 py-2 text-sm font-medium flex items-center gap-2 z-20 hover:bg-gray-800"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
            {showAnnotated ? 'Hide Skeleton' : 'Show Skeleton'}
          </button>
        )}

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

        {/* Pose Analysis Data */}
        {poseData && (
          <div className="bg-gray-800/50 rounded-xl p-4 mb-6 border border-gray-700">
            <p className="text-gray-400 text-xs uppercase mb-3 font-bold">Pose Analysis</p>

            {/* Key Metrics */}
            <div className="grid grid-cols-2 gap-3 mb-4">
              <div className="bg-gray-900/50 rounded-lg p-3">
                <span className="text-gray-500 text-xs block">Elbow Angle</span>
                <span className="text-white text-lg font-mono">{poseData.contact_elbow_angle.toFixed(1)}°</span>
              </div>
              <div className="bg-gray-900/50 rounded-lg p-3">
                <span className="text-gray-500 text-xs block">Knee Angle</span>
                <span className="text-white text-lg font-mono">{poseData.contact_knee_angle.toFixed(1)}°</span>
              </div>
              <div className="bg-gray-900/50 rounded-lg p-3">
                <span className="text-gray-500 text-xs block">Torso Rotation</span>
                <span className="text-white text-lg font-mono">{poseData.max_torso_rotation.toFixed(1)}°</span>
              </div>
              <div className="bg-gray-900/50 rounded-lg p-3">
                <span className="text-gray-500 text-xs block">Follow Through</span>
                <span className="text-white text-lg font-mono">{poseData.follow_through_completion.toFixed(0)}%</span>
              </div>
            </div>

            {/* All Angles at Contact Point */}
            {poseData.contact_angles && (
              <div className="bg-gray-900/30 rounded-lg p-3 mb-4">
                <p className="text-gray-500 text-xs uppercase mb-2 font-bold">All Joint Angles (Contact)</p>
                <div className="grid grid-cols-3 gap-2 text-xs">
                  {/* Arm Angles */}
                  <div className="col-span-3 text-gray-400 font-bold mt-1">Arms</div>
                  {['left_elbow', 'right_elbow', 'left_shoulder', 'right_shoulder', 'left_wrist', 'right_wrist'].map(key => {
                    const value = poseData.contact_angles?.[key];
                    return value != null ? (
                      <div key={key} className="flex justify-between">
                        <span className="text-gray-500 capitalize">{key.replace('_', ' ')}</span>
                        <span className="text-white font-mono">{value.toFixed(1)}°</span>
                      </div>
                    ) : null;
                  })}
                  {/* Leg Angles */}
                  <div className="col-span-3 text-gray-400 font-bold mt-2">Legs</div>
                  {['left_hip', 'right_hip', 'left_knee', 'right_knee', 'left_ankle', 'right_ankle'].map(key => {
                    const value = poseData.contact_angles?.[key];
                    return value != null ? (
                      <div key={key} className="flex justify-between">
                        <span className="text-gray-500 capitalize">{key.replace('_', ' ')}</span>
                        <span className="text-white font-mono">{value.toFixed(1)}°</span>
                      </div>
                    ) : null;
                  })}
                </div>
              </div>
            )}

            {/* Posture Metrics */}
            {poseData.contact_posture && (
              <div className="bg-gray-900/30 rounded-lg p-3 mb-4">
                <p className="text-gray-500 text-xs uppercase mb-2 font-bold">Posture Metrics (Contact)</p>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  {Object.entries(poseData.contact_posture).map(([key, value]) =>
                    value != null ? (
                      <div key={key} className="flex justify-between">
                        <span className="text-gray-500 capitalize">{key.replace('_', ' ')}</span>
                        <span className="text-white font-mono">{(value as number).toFixed(1)}°</span>
                      </div>
                    ) : null
                  )}
                </div>
              </div>
            )}

            {/* Full body time series charts */}
            {poseData.frames && poseData.frames.length > 0 && (
              <FullBodyTimeline frames={poseData.frames} />
            )}

            {/* Frame count */}
            <div className="text-gray-500 text-xs text-center mt-3">
              Analyzed {poseData.total_frames} frames
            </div>
          </div>
        )}

        {/* Pro Tip */}
        <div className="bg-tennis/10 border border-tennis/30 rounded-lg p-4 mb-6">
          <p className="text-sm text-tennis font-medium flex items-center gap-2">
            <span>💡</span>
            <span>{poseData?.recommendations?.[0] || "Practice this 10 times slowly, then at full speed."}</span>
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