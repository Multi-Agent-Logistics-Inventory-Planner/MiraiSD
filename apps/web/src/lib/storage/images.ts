const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
const ALLOWED_MIME_TYPES = ["image/jpeg", "image/png", "image/webp"];
const WEBP_MAX_EDGE = 1600;
const WEBP_QUALITY = 0.82;

// Magic numbers for image format validation
const MAGIC_NUMBERS: Record<string, number[][]> = {
  "image/jpeg": [[0xff, 0xd8, 0xff]],
  "image/png": [[0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]],
  "image/webp": [[0x52, 0x49, 0x46, 0x46]], // RIFF header (WebP starts with RIFF)
};

export interface UploadResult {
  url: string;
  path: string;
}

export interface UploadError {
  message: string;
  code: "FILE_TOO_LARGE" | "INVALID_TYPE" | "UPLOAD_FAILED" | "NO_CLIENT";
}

function sanitizeFilename(filename: string): string {
  return filename
    .toLowerCase()
    .replace(/[^a-z0-9.-]/g, "-")
    .replace(/-+/g, "-")
    .slice(0, 100);
}

function generateUniqueFilename(originalFilename: string): string {
  const uuid = crypto.randomUUID();
  const sanitized = sanitizeFilename(originalFilename);
  return `${uuid}_${sanitized}`;
}

function replaceExtensionWithWebp(filename: string): string {
  const dotIndex = filename.lastIndexOf(".");
  const base = dotIndex >= 0 ? filename.slice(0, dotIndex) : filename;
  return `${base}.webp`;
}

async function convertToWebp(file: File): Promise<Blob | null> {
  if (
    typeof createImageBitmap !== "function" ||
    typeof OffscreenCanvas === "undefined"
  ) {
    return null;
  }

  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
    const { width, height } = bitmap;
    const longest = Math.max(width, height);
    const scale = longest > WEBP_MAX_EDGE ? WEBP_MAX_EDGE / longest : 1;
    const targetW = Math.max(1, Math.round(width * scale));
    const targetH = Math.max(1, Math.round(height * scale));

    const canvas = new OffscreenCanvas(targetW, targetH);
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      bitmap.close();
      return null;
    }
    ctx.drawImage(bitmap, 0, 0, targetW, targetH);
    bitmap.close();

    const blob = await canvas.convertToBlob({
      type: "image/webp",
      quality: WEBP_QUALITY,
    });
    return blob;
  } catch {
    return null;
  }
}

async function validateMagicNumber(file: File): Promise<boolean> {
  const expectedPatterns = MAGIC_NUMBERS[file.type];
  if (!expectedPatterns) return false;

  const buffer = await file.slice(0, 12).arrayBuffer();
  const bytes = new Uint8Array(buffer);

  return expectedPatterns.some((pattern) =>
    pattern.every((byte, i) => bytes[i] === byte)
  );
}

export function validateFile(file: File): UploadError | null {
  if (file.size > MAX_FILE_SIZE) {
    return {
      message: `File size must be less than ${MAX_FILE_SIZE / 1024 / 1024}MB`,
      code: "FILE_TOO_LARGE",
    };
  }

  if (!ALLOWED_MIME_TYPES.includes(file.type)) {
    return {
      message: "Only JPEG, PNG, and WebP images are allowed",
      code: "INVALID_TYPE",
    };
  }

  return null;
}

export async function uploadProductImage(
  file: File
): Promise<UploadResult | UploadError> {
  const validationError = validateFile(file);
  if (validationError) {
    return validationError;
  }

  const validMagic = await validateMagicNumber(file);
  if (!validMagic) {
    return {
      message: "File content does not match declared image type",
      code: "INVALID_TYPE",
    };
  }

  const webpBlob = await convertToWebp(file);
  const uploadBody: Blob = webpBlob ?? file;
  const uploadContentType = webpBlob ? "image/webp" : file.type;
  const baseFilename = generateUniqueFilename(file.name);
  const filename = webpBlob
    ? replaceExtensionWithWebp(baseFilename)
    : baseFilename;

  const blobForUpload =
    uploadBody.type === uploadContentType
      ? uploadBody
      : new Blob([uploadBody], { type: uploadContentType });

  const formData = new FormData();
  formData.append("file", blobForUpload, filename);
  formData.append("filename", filename);

  const response = await fetch("/api/images/upload", {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    const message = await response.text().catch(() => "");
    return {
      message: message || `Upload failed (${response.status})`,
      code: "UPLOAD_FAILED",
    };
  }

  const { url, key } = (await response.json()) as { url: string; key: string };
  return { url, path: key };
}

export async function deleteProductImage(
  url: string
): Promise<{ success: true } | UploadError> {
  const response = await fetch("/api/images/delete", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ url }),
  });

  if (!response.ok) {
    const message = await response.text().catch(() => "");
    return {
      message: message || `Delete failed (${response.status})`,
      code: "UPLOAD_FAILED",
    };
  }

  return { success: true };
}

export function isUploadError(
  result: UploadResult | UploadError | { success: true }
): result is UploadError {
  return (
    "code" in result &&
    "message" in result &&
    !("url" in result) &&
    !("success" in result)
  );
}
