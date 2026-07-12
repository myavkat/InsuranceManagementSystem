import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { FileQuestion } from "lucide-react";

export default function NotFound() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="flex flex-col items-center gap-4 text-center">
        <FileQuestion className="size-12 text-muted-foreground/50" />
        <div>
          <h2 className="text-lg font-semibold">Page not found</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            The page you're looking for doesn't exist.
          </p>
        </div>
        <Link href="/dashboard" className={cn(buttonVariants(), "gap-2")}>
          Go to Dashboard
        </Link>
      </div>
    </div>
  );
}
