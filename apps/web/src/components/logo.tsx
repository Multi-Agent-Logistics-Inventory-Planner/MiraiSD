import Image from "next/image";
import { cn } from "@/lib/utils";

interface LogoProps {
  className?: string;
  width?: number;
  height?: number;
}

export function Logo({ className, width = 32, height = 32 }: LogoProps) {
  return (
    <>
      <Image
        src="/mirai-logo.png"
        alt="Mirai Logo"
        width={width}
        height={height}
        className={cn("dark:hidden", className)}
        priority
      />
      <Image
        src="/mirai-logo-dark.png"
        alt="Mirai Logo"
        width={width}
        height={height}
        className={cn("hidden dark:block", className)}
        priority
      />
    </>
  );
}
