import React from 'react';
import { Button } from './Button';

interface Props {
  onStart: () => void;
}

export const LandingPage: React.FC<Props> = ({ onStart }) => {
  return (
    <div className="h-full flex flex-col justify-between p-6 bg-gradient-to-b from-court-dark to-black text-white relative overflow-hidden">
      {/* Background Decor */}
      <div className="absolute top-[-10%] right-[-10%] w-64 h-64 bg-tennis opacity-10 rounded-full blur-3xl pointer-events-none"></div>
      
      <div className="flex-1 flex flex-col justify-center items-center text-center mt-10">
        <div className="mb-6 animate-fade-in-up">
           <span className="text-6xl">🎾</span>
        </div>
        
        <h1 className="text-5xl font-black tracking-tighter mb-4 leading-tight">
          AI Tennis <br />
          <span className="text-tennis">Coach</span>
        </h1>
        
        <p className="text-gray-400 text-lg max-w-xs mx-auto leading-relaxed">
          Record one swing. <br/> Get coach-like feedback.
        </p>
      </div>

      <div className="mb-8 w-full z-10">
        <Button onClick={onStart} fullWidth>
          ▶ Start Recording
        </Button>
      </div>
    </div>
  );
};