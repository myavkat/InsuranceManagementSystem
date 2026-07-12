import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { type InputHTMLAttributes, forwardRef } from "react";

interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  inputClassName?: string;
}

export const FormField = forwardRef<HTMLInputElement, FormFieldProps>(
  function FormField({ label, error, className, inputClassName, id, ...props }, ref) {
    const fieldId = id ?? props.name;
    return (
      <div className={cn("space-y-1.5", className)}>
        <label
          htmlFor={fieldId}
          className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
        >
          {label}
        </label>
        <Input
          ref={ref}
          id={fieldId}
          className={cn(error && "border-destructive", inputClassName)}
          aria-invalid={!!error}
          {...props}
        />
        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
      </div>
    );
  }
);
