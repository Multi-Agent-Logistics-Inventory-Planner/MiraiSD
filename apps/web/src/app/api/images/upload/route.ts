import { NextResponse } from "next/server";
import { PutObjectCommand } from "@aws-sdk/client-s3";
import { getServerUser } from "@/lib/supabase/server";
import { buildPublicUrl, getR2Client, R2_BUCKET } from "@/lib/r2/client";

const MAX_BODY_BYTES = 5 * 1024 * 1024;
const ALLOWED_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
]);

export async function POST(request: Request) {
  const user = await getServerUser();
  if (!user) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const formData = await request.formData();
  const file = formData.get("file");
  const filename = formData.get("filename");

  if (!(file instanceof Blob)) {
    return NextResponse.json({ error: "file required" }, { status: 400 });
  }
  if (typeof filename !== "string" || filename.length === 0) {
    return NextResponse.json({ error: "filename required" }, { status: 400 });
  }
  if (file.size > MAX_BODY_BYTES) {
    return NextResponse.json({ error: "file too large" }, { status: 413 });
  }
  if (!ALLOWED_TYPES.has(file.type)) {
    return NextResponse.json({ error: "invalid type" }, { status: 415 });
  }
  if (filename.includes("/") || filename.includes("..")) {
    return NextResponse.json({ error: "invalid filename" }, { status: 400 });
  }

  const arrayBuffer = await file.arrayBuffer();
  const body = new Uint8Array(arrayBuffer);

  await getR2Client().send(
    new PutObjectCommand({
      Bucket: R2_BUCKET,
      Key: filename,
      Body: body,
      ContentType: file.type,
      CacheControl: "public, max-age=604800, immutable",
    })
  );

  return NextResponse.json({ url: buildPublicUrl(filename), key: filename });
}
