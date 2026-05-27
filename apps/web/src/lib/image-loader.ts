interface LoaderProps {
  src: string;
  width: number;
  quality?: number;
}

const R2_BASE = "https://img.mirai-inventory.com";

export default function imageLoader({ src, width, quality }: LoaderProps): string {
  if (src.startsWith(`${R2_BASE}/`) && !src.includes("/cdn-cgi/image/")) {
    const key = src.slice(R2_BASE.length + 1);
    const params = `width=${width},quality=${quality ?? 75},format=auto`;
    return `${R2_BASE}/cdn-cgi/image/${params}/${key}`;
  }
  return src;
}
