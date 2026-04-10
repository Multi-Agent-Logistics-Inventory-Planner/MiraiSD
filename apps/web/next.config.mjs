import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/** @type {import("next").NextConfig} */
const nextConfig = {
  output: 'standalone',
  reactCompiler: true,
  turbopack: {
    root: __dirname,
  },
  typescript: {
    ignoreBuildErrors: false,
  },
  images: {
    formats: ["image/avif", "image/webp"],
    minimumCacheTTL: 60 * 60 * 24 * 7,
    remotePatterns: [
      {
        protocol: "https",
        hostname: "*.supabase.co",
        pathname: "/storage/v1/object/public/**",
      },
    ],
  },
  async headers() {
    // Detect local development by checking if backend URL contains localhost
    const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL || process.env.NEXT_PUBLIC_API_URL || '';
    const isLocalDevelopment = backendUrl.includes('localhost');

    // Environment-specific CSP: allow localhost only in development
    // Production CSP includes actual backend domain and WebSocket connections
    const connectSrc = isLocalDevelopment
      ? "'self' http://localhost:3000 http://localhost:4000 https://*.supabase.co wss://*.supabase.co"
      : `'self' https://*.supabase.co wss://*.supabase.co http://*.mirai-inventory.com https://*.mirai-inventory.com`;

    // React 19 dev mode uses eval() for source-map reconstruction and Fast
    // Refresh; allow it only in development. Production never ships eval().
    const isDev = process.env.NODE_ENV !== 'production';
    const scriptSrc = isDev
      ? "'self' 'unsafe-inline' 'unsafe-eval' https://vercel.live"
      : "'self' 'unsafe-inline' https://vercel.live";

    return [
      {
        source: '/:path*',
        headers: [
          {
            key: 'Content-Security-Policy',
            value: `default-src 'self'; script-src ${scriptSrc}; style-src 'self' 'unsafe-inline'; img-src 'self' data: https: blob:; font-src 'self' data:; connect-src ${connectSrc}; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`
          },
          {
            key: 'X-Frame-Options',
            value: 'DENY'
          },
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff'
          },
          {
            key: 'X-XSS-Protection',
            value: '1; mode=block'
          },
          {
            key: 'Referrer-Policy',
            value: 'strict-origin-when-cross-origin'
          },
          {
            key: 'Permissions-Policy',
            value: 'camera=(), microphone=(), geolocation=()'
          },
          {
            key: 'Strict-Transport-Security',
            value: 'max-age=31536000; includeSubDomains; preload'
          }
        ]
      }
    ];
  }
};

export default nextConfig;

