interface LoaderProps {
  src: string;
  width: number;
  quality?: number;
}

const R2_BASE = process.env.NEXT_PUBLIC_R2_PUBLIC_BASE_URL ?? "";

export default function imageLoader({ src, width, quality }: LoaderProps): string {
  if (R2_BASE && src.startsWith(`${R2_BASE}/`) && !src.includes("/cdn-cgi/image/")) {
    const key = src.slice(R2_BASE.length + 1);
    const params = `width=${width},quality=${quality ?? 75},format=auto`;
    return `${R2_BASE}/cdn-cgi/image/${params}/${key}`;
  }
  return src;
}
