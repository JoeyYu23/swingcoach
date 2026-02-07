import React, { useState } from 'react';
import { ViewState, AppState, AnalysisResult } from './types';
import { LandingPage } from './components/LandingPage';
import { RecordingPage } from './components/RecordingPage';
import { ResultsPage } from './components/ResultsPage';
import { analyzeSwingComplete } from './services/geminiService';

const App: React.FC = () => {
  const [state, setState] = useState<AppState>({
    view: 'landing',
    videoFile: null,
    videoPreviewUrl: null,
    result: null,
    error: null
  });

  const handleStart = () => {
    setState(prev => ({ ...prev, view: 'recording' }));
  };

  const handleFileSelect = (file: File) => {
    if (state.videoPreviewUrl) {
      URL.revokeObjectURL(state.videoPreviewUrl);
    }
    const url = URL.createObjectURL(file);
    setState(prev => ({ ...prev, videoFile: file, videoPreviewUrl: url }));
  };

  const handleRetake = () => {
     if (state.videoPreviewUrl) {
      URL.revokeObjectURL(state.videoPreviewUrl);
    }
    setState(prev => ({ ...prev, videoFile: null, videoPreviewUrl: null }));
  };

  const handleAnalyze = async (rotation: number = 0) => {
    if (!state.videoFile) return;

    setState(prev => ({ ...prev, view: 'analyzing', error: null }));

    try {
      const result = await analyzeSwingComplete(state.videoFile, rotation);
      setState(prev => ({
        ...prev,
        view: 'results',
        result: result
      }));
    } catch (err: any) {
      console.error(err);
      setState(prev => ({
        ...prev,
        view: 'recording', // Go back to recording so they can try again
        error: "Analysis failed. Please try a shorter video or different angle."
      }));
      alert("Failed to analyze video. Please ensure API Key is set and video is valid.");
    }
  };

  const handleReset = () => {
     if (state.videoPreviewUrl) {
      URL.revokeObjectURL(state.videoPreviewUrl);
    }
    setState({
      view: 'landing',
      videoFile: null,
      videoPreviewUrl: null,
      result: null,
      error: null
    });
  };

  // View Routing
  return (
    <div className="w-full h-screen bg-black overflow-hidden font-sans">
      {state.view === 'landing' && (
        <LandingPage onStart={handleStart} />
      )}
      
      {(state.view === 'recording' || state.view === 'analyzing') && (
        <RecordingPage 
          onFileSelect={handleFileSelect} 
          videoPreview={state.videoPreviewUrl}
          onAnalyze={handleAnalyze}
          isAnalyzing={state.view === 'analyzing'}
          onRetake={handleRetake}
        />
      )}

      {state.view === 'results' && state.result && (
        <ResultsPage 
          result={state.result} 
          videoPreview={state.videoPreviewUrl} 
          onReset={handleReset}
        />
      )}
    </div>
  );
};

export default App;