import { S3Client } from "@aws-sdk/client-s3";

const accountId = process.env.R2_ACCOUNT_ID;
const accessKeyId = process.env.R2_ACCESS_KEY_ID;
const secretAccessKey = process.env.R2_SECRET_ACCESS_KEY;

export const R2_BUCKET = process.env.R2_BUCKET ?? "product-images";
export const R2_PUBLIC_BASE_URL =
  process.env.NEXT_PUBLIC_R2_PUBLIC_BASE_URL ?? "";

let _client: S3Client | null = null;

export function getR2Client(): S3Client {
  if (_client) return _client;
  if (!accountId || !accessKeyId || !secretAccessKey) {
    throw new Error(
      "R2 credentials missing — set R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY"
    );
  }
  _client = new S3Client({
    region: "auto",
    endpoint: `https://${accountId}.r2.cloudflarestorage.com`,
    credentials: { accessKeyId, secretAccessKey },
    requestChecksumCalculation: "WHEN_REQUIRED",
    responseChecksumValidation: "WHEN_REQUIRED",
  });
  return _client;
}

export function buildPublicUrl(key: string): string {
  if (!R2_PUBLIC_BASE_URL) {
    throw new Error("NEXT_PUBLIC_R2_PUBLIC_BASE_URL not set");
  }
  return `${R2_PUBLIC_BASE_URL}/${encodeURI(key)}`;
}

export function extractKeyFromUrl(url: string): string | null {
  if (!url) return null;
  if (R2_PUBLIC_BASE_URL && url.startsWith(`${R2_PUBLIC_BASE_URL}/`)) {
    return decodeURI(url.slice(R2_PUBLIC_BASE_URL.length + 1));
  }
  const legacyMatch = url.match(/\/product-images\/(.+)$/);
  return legacyMatch ? legacyMatch[1] : null;
}
