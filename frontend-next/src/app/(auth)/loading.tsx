import { Skeleton } from "@/components/ui/skeleton";

export default function AuthLoading() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/50 px-4">
      <div className="w-full max-w-md space-y-4">
        <Skeleton className="mx-auto h-8 w-48" />
        <Skeleton className="h-64 w-full rounded-lg" />
      </div>
    </main>
  );
}
