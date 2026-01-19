import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/** @type {import("next").NextConfig} */
const nextConfig = {
  // Keep this minimal; we just need the app to boot reliably.
  reactCompiler: true,
  turbopack: {
    // Monorepo: avoid Next.js inferring the wrong workspace root when multiple lockfiles exist.
    root: __dirname,
  },
};

export default nextConfig;

