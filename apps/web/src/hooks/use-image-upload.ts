"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import {
  uploadProductImage,
  validateFile,
  isUploadError,
} from "@/lib/supabase/storage";

interface ImageUploadState {
  file: File | null;
  previewUrl: string | null;
  uploadedUrl: string | null;
  isUploading: boolean;
  error: string | null;
}

const initialState: ImageUploadState = {
  file: null,
  previewUrl: null,
  uploadedUrl: null,
  isUploading: false,
  error: null,
};

export function useImageUpload(existingUrl?: string | null) {
  const [state, setState] = useState<ImageUploadState>(() => ({
    ...initialState,
    uploadedUrl: existingUrl ?? null,
  }));

  // Track preview URL for cleanup on unmount
  const previewUrlRef = useRef<string | null>(null);

  // Cleanup object URL on unmount to prevent memory leaks
  useEffect(() => {
    return () => {
      if (previewUrlRef.current) {
        URL.revokeObjectURL(previewUrlRef.current);
      }
    };
  }, []);

  const selectFile = useCallback((file: File | null) => {
    if (!file) {
      setState((prev) => {
        if (prev.previewUrl) {
          URL.revokeObjectURL(prev.previewUrl);
          previewUrlRef.current = null;
        }
        return {
          ...prev,
          file: null,
          previewUrl: null,
          error: null,
        };
      });
      return;
    }

    const validationError = validateFile(file);
    if (validationError) {
      setState((prev) => ({
        ...prev,
        error: validationError.message,
      }));
      return;
    }

    const newPreviewUrl = URL.createObjectURL(file);
    previewUrlRef.current = newPreviewUrl;

    setState((prev) => {
      // Revoke old preview URL to prevent memory leaks
      if (prev.previewUrl) {
        URL.revokeObjectURL(prev.previewUrl);
      }
      return {
        ...prev,
        file,
        previewUrl: newPreviewUrl,
        error: null,
      };
    });
  }, []);

  const upload = useCallback(async (): Promise<string | null> => {
    if (!state.file) {
      // No new file selected, return existing URL if any
      return state.uploadedUrl;
    }

    setState((prev) => ({ ...prev, isUploading: true, error: null }));

    const result = await uploadProductImage(state.file);

    if (isUploadError(result)) {
      setState((prev) => ({
        ...prev,
        isUploading: false,
        error: result.message,
      }));
      return null;
    }

    setState((prev) => {
      // Revoke preview URL after successful upload
      if (prev.previewUrl) {
        URL.revokeObjectURL(prev.previewUrl);
        previewUrlRef.current = null;
      }
      return {
        ...prev,
        file: null,
        previewUrl: null,
        uploadedUrl: result.url,
        isUploading: false,
        error: null,
      };
    });

    return result.url;
  }, [state.file, state.uploadedUrl]);

  const clear = useCallback(() => {
    setState((prev) => {
      if (prev.previewUrl) {
        URL.revokeObjectURL(prev.previewUrl);
        previewUrlRef.current = null;
      }
      return initialState;
    });
  }, []);

  const reset = useCallback((url?: string | null) => {
    setState((prev) => {
      if (prev.previewUrl) {
        URL.revokeObjectURL(prev.previewUrl);
        previewUrlRef.current = null;
      }
      return {
        ...initialState,
        uploadedUrl: url ?? null,
      };
    });
  }, []);

  const displayUrl = state.previewUrl ?? state.uploadedUrl;
  const hasNewFile = state.file !== null;
  const hasImage = displayUrl !== null;

  return {
    ...state,
    displayUrl,
    hasNewFile,
    hasImage,
    selectFile,
    upload,
    clear,
    reset,
  };
}
