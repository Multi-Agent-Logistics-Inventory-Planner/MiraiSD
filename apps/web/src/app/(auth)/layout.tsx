export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="h-dvh flex items-center justify-center bg-linear-to-br from-slate-50 to-slate-100 dark:from-[#262624] dark:to-[#262624] p-4 overflow-hidden">
      <div className="w-full max-w-md max-h-full overflow-y-auto">
        {children}
      </div>
    </div>
  );
}
