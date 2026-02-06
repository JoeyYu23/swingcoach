import { GoogleGenAI, Type, Modality } from "@google/genai";
import { AnalysisResult } from "../types";

// NOTE: In a real production app, never expose API keys on the frontend.
// This is for Hackathon/Demo purposes only as requested.
const apiKey = import.meta.env.VITE_GEMINI_API_KEY || '';
const ai = new GoogleGenAI({ apiKey });

/**
 * Converts a File object to a Base64 string.
 */
const fileToGenerativePart = async (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      const base64String = reader.result as string;
      // Remove data url prefix (e.g. "data:video/mp4;base64,")
      const base64Data = base64String.split(',')[1];
      resolve(base64Data);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
};

/**
 * Orchestrates the analysis:
 * 1. Uploads/Sends video to Gemini Vision.
 * 2. Gets text analysis.
 * 3. Sends text to Gemini TTS.
 */
export const analyzeSwing = async (file: File): Promise<AnalysisResult> => {
  try {
    const base64Video = await fileToGenerativePart(file);

    // 1. Vision Analysis
    // Using gemini-3-flash-preview as it is the current recommended multimodal model for fast tasks.
    const model = "gemini-3-flash-preview"; 
    
    const analysisResponse = await ai.models.generateContent({
      model: model,
      contents: {
        parts: [
          {
            inlineData: {
              mimeType: file.type,
              data: base64Video
            }
          },
          {
            text: `You are a professional, direct, and elite Tennis Coach. 
            Analyze this short video clip of a tennis swing. 
            
            Identify the ONE single biggest flaw that the player needs to fix immediately.
            Do not give a list. Give one specific correction.
            
            Return the result in JSON format with these fields:
            - feedback_text: A direct, spoken-style command to the player (max 15 words). e.g., "You are muscling it. Drop the racket head sooner."
            - metric_name: A technical term for the issue. e.g., "Contact Point", "Racket Drop", "Follow Through".
            - metric_value: The specific observation. e.g., "Too Close to Body", "Late", "Abbreviated".
            `
          }
        ]
      },
      config: {
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.OBJECT,
          properties: {
            feedback_text: { type: Type.STRING },
            metric_name: { type: Type.STRING },
            metric_value: { type: Type.STRING },
          },
          required: ["feedback_text", "metric_name", "metric_value"]
        }
      }
    });

    const textResponse = analysisResponse.text;
    if (!textResponse) throw new Error("No analysis received from AI.");
    
    const resultData = JSON.parse(textResponse) as AnalysisResult;

    // 2. Audio Generation (TTS)
    // We generate audio for the feedback text to create "The Moment".
    try {
      const ttsResponse = await ai.models.generateContent({
        model: "gemini-2.5-flash-preview-tts",
        contents: {
            parts: [{ text: resultData.feedback_text }]
        },
        config: {
          responseModalities: [Modality.AUDIO],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: { voiceName: "Puck" } // Deep, authoritative voice
            }
          }
        }
      });

      const audioPart = ttsResponse.candidates?.[0]?.content?.parts?.[0];
      if (audioPart && audioPart.inlineData && audioPart.inlineData.data) {
        resultData.audio_base64 = audioPart.inlineData.data;
      }
    } catch (ttsError) {
      console.warn("TTS Generation failed, falling back to text only", ttsError);
      // Non-blocking error, we still return the text analysis
    }

    return resultData;

  } catch (error) {
    console.error("Gemini Analysis Error:", error);
    throw error;
  }
};