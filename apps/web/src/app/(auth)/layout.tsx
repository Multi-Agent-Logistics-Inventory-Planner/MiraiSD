export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-linear-to-br from-slate-50 to-slate-100 dark:from-[#262624] dark:to-[#262624] p-4">
      <div className="w-full max-w-md">
        {children}
      </div>
    </div>
  );
}
