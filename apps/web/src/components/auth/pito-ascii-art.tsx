"use client";

import { useEffect, useRef } from "react";

const CELL_SIZE = 6;

type BackgroundPixel = {
  x: number;
  y: number;
  phase: number;
  size: number;
};

function prefersReducedMotion() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function PitoAsciiArt() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const context = canvas.getContext("2d");
    if (!context) return;

    const image = new Image();
    let animationFrame: number | undefined;
    let disposed = false;
    let width = 0;
    let height = 0;
    let pitoMask: Uint8ClampedArray | undefined;
    let sparkleMask: Uint8ClampedArray | undefined;
    let backgroundPixels: BackgroundPixel[] = [];
    let reducedMotion = prefersReducedMotion();

    const draw = (time = 0) => {
      if (!pitoMask || !sparkleMask || !width || !height) return;

      const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      context.clearRect(0, 0, width, height);

      const glowRadius = Math.min(width, height) * 0.48;
      const glow = context.createRadialGradient(
        width / 2,
        height / 2,
        0,
        width / 2,
        height / 2,
        glowRadius
      );
      glow.addColorStop(0, "rgba(255, 255, 255, 0.13)");
      glow.addColorStop(0.55, "rgba(255, 255, 255, 0.035)");
      glow.addColorStop(1, "rgba(255, 255, 255, 0)");
      context.fillStyle = glow;
      context.fillRect(0, 0, width, height);

      const animationTime = reducedMotion ? 0 : time / 900;
      const backgroundTime = reducedMotion ? 0 : time / 1800;

      // Keep these deliberately quiet: they give the panel depth without
      // competing with the large Pito silhouette or its three sparkles.
      for (const pixel of backgroundPixels) {
        const twinkle =
          Math.sin(backgroundTime + pixel.phase) * 0.3 + 0.55;
        context.fillStyle = `rgba(255, 255, 255, ${twinkle})`;
        context.fillRect(pixel.x, pixel.y, pixel.size, pixel.size);
      }

      const drawMask = (
        mask: Uint8ClampedArray,
        offsetX = 0,
        offsetY = 0
      ) => {
        for (let y = CELL_SIZE / 2; y < height; y += CELL_SIZE) {
          for (let x = CELL_SIZE / 2; x < width; x += CELL_SIZE) {
            const sampleX = Math.floor(x - offsetX);
            const sampleY = Math.floor(y - offsetY);
            if (
              sampleX < 0 ||
              sampleX >= width ||
              sampleY < 0 ||
              sampleY >= height
            ) {
              continue;
            }
          const pixelOffset = (sampleY * width + sampleX) * 4;
          const red = mask[pixelOffset];
          const green = mask[pixelOffset + 1];
          const blue = mask[pixelOffset + 2];
          const alpha = mask[pixelOffset + 3] / 255;

          if (alpha < 0.08) continue;

            const shimmer =
              Math.sin(sampleX * 0.045 + animationTime) *
                Math.cos(sampleY * 0.035 - animationTime * 0.7) *
              0.18 +
              0.82;
            const luminance =
              (red * 0.2126 + green * 0.7152 + blue * 0.0722) / 255;
            const intensity = alpha * (0.45 + luminance * 0.55) * shimmer;
            const whiteShade = Math.round(220 + luminance * 35);
            const blockSize = 1.5 + intensity * 3;

            context.fillStyle = `rgba(${whiteShade}, ${whiteShade}, ${whiteShade}, ${0.58 + intensity * 0.42})`;
            context.fillRect(
              Math.round(x - blockSize / 2),
              Math.round(y - blockSize / 2),
              Math.round(blockSize),
              Math.round(blockSize)
            );
          }
        }
      };

      // The gentle drift keeps Pito legible while making the panel feel alive.
      // no longer feels completely fixed against the panel background.
      const floatTime = reducedMotion ? 0 : time / 1800;
      const floatX = Math.sin(floatTime) * 8;
      const floatY = Math.cos(floatTime * 0.8) * 5;
      drawMask(pitoMask, floatX, floatY);
      drawMask(sparkleMask, floatX, floatY);

      if (!reducedMotion && !disposed) {
        animationFrame = window.requestAnimationFrame(draw);
      }
    };

    const resize = () => {
      const bounds = canvas.getBoundingClientRect();
      const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
      width = Math.max(1, Math.floor(bounds.width));
      height = Math.max(1, Math.floor(bounds.height));
      canvas.width = Math.floor(width * pixelRatio);
      canvas.height = Math.floor(height * pixelRatio);

      const maskCanvas = document.createElement("canvas");
      maskCanvas.width = width;
      maskCanvas.height = height;
      const maskContext = maskCanvas.getContext("2d", { willReadFrequently: true });
      if (!maskContext) return;

      // Pito's transparent source canvas has generous padding. Deliberately
      // overscale and offset it so the visible silhouette behaves like a hero
      // graphic rather than a centered icon, including subtle edge cropping.
      const imageSize = Math.min(width * 1.95, height * 1.8);
      maskContext.drawImage(
        image,
        width * 0.46 - imageSize / 2,
        height * 0.56 - imageSize / 2,
        imageSize,
        imageSize
      );
      pitoMask = maskContext.getImageData(0, 0, width, height).data;

      maskContext.clearRect(0, 0, width, height);

      // The exact four-point sparkle from Pito's source image. Drawing these
      // into the same mask makes every repeated star use Pito's original
      // pixel-block treatment rather than a generic plus symbol.
      const sparkleSource = { x: 1158, y: 688, width: 148, height: 229 };
      const drawSparkle = (x: number, y: number, sparkleWidth: number) => {
        const sparkleHeight =
          sparkleWidth * (sparkleSource.height / sparkleSource.width);
        maskContext.drawImage(
          image,
          sparkleSource.x,
          sparkleSource.y,
          sparkleSource.width,
          sparkleSource.height,
          x,
          y,
          sparkleWidth,
          sparkleHeight
        );
      };

      drawSparkle(width * 0.1, height * 0.18, width * 0.11);
      drawSparkle(width * 0.78, height * 0.14, width * 0.135);
      drawSparkle(width * 0.13, height * 0.82, width * 0.09);

      sparkleMask = maskContext.getImageData(0, 0, width, height).data;

      // Deterministic, sparse background pixels. Their slight opacity shift
      // provides atmosphere while keeping the decorative field unobtrusive.
      backgroundPixels = [];
      for (let y = 24; y < height; y += 42) {
        for (let x = 24; x < width; x += 42) {
          const seed = Math.sin(x * 12.9898 + y * 78.233) * 43758.5453;
          const value = seed - Math.floor(seed);
          if (value > 0.3) continue;

          backgroundPixels.push({
            x: x + ((value * 37) % 18),
            y: y + ((value * 53) % 18),
            phase: value * Math.PI * 2,
            size: value > 0.1 ? 2 : 3,
          });
        }
      }

      if (animationFrame) window.cancelAnimationFrame(animationFrame);
      draw();
    };

    const motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    const handleMotionChange = () => {
      reducedMotion = motionQuery.matches;
      if (animationFrame) window.cancelAnimationFrame(animationFrame);
      draw();
    };
    const resizeObserver = new ResizeObserver(resize);

    image.onload = () => {
      resizeObserver.observe(canvas);
      resize();
    };
    image.src = "/pito.webp";
    motionQuery.addEventListener("change", handleMotionChange);

    return () => {
      disposed = true;
      if (animationFrame) window.cancelAnimationFrame(animationFrame);
      resizeObserver.disconnect();
      motionQuery.removeEventListener("change", handleMotionChange);
    };
  }, []);

  return <canvas ref={canvasRef} aria-hidden="true" className="absolute inset-0 h-full w-full" />;
}
