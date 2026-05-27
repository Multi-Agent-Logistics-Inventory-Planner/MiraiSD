import { NextResponse } from "next/server";
import { DeleteObjectCommand, S3ServiceException } from "@aws-sdk/client-s3";
import { getServerUser } from "@/lib/supabase/server";
import { extractKeyFromUrl, getR2Client, R2_BUCKET } from "@/lib/r2/client";

export async function POST(request: Request) {
  const user = await getServerUser();
  if (!user) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { url } = (await request.json()) as { url?: string };
  if (typeof url !== "string" || url.length === 0) {
    return NextResponse.json({ error: "url required" }, { status: 400 });
  }

  const key = extractKeyFromUrl(url);
  if (!key) {
    return NextResponse.json({ error: "could not parse key" }, { status: 400 });
  }

  try {
    await getR2Client().send(
      new DeleteObjectCommand({ Bucket: R2_BUCKET, Key: key })
    );
  } catch (err) {
    if (err instanceof S3ServiceException && err.name === "NoSuchKey") {
      return NextResponse.json({ success: true, alreadyGone: true });
    }
    throw err;
  }

  return NextResponse.json({ success: true });
}
