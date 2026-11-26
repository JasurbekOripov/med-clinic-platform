import { ChevronDown } from "lucide-react";
import * as React from "react";
import { caretAfterDigits, groupDigits, parseAmount } from "@medclinic/web-ui/lib/money";
import { formatPhoneInput, nationalDigits } from "@medclinic/web-ui/lib/phone";
import { cn } from "@medclinic/web-ui/lib/utils";

const fieldClass =
  "w-full rounded-lg border border-border bg-surface px-3 text-sm shadow-xs placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-danger";

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(({ className, ...props }, ref) => (
  <input ref={ref} className={cn(fieldClass, "h-10", className)} {...props} />
));
Input.displayName = "Input";

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => <textarea ref={ref} className={cn(fieldClass, "min-h-20 py-2", className)} {...props} />,
);
Textarea.displayName = "Textarea";

/**
 * Native select (fully keyboard accessible: type-to-select) with the browser's own arrow removed, so padding
 * and the chevron look the same in Safari, Chrome and Firefox.
 */
export const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, ...props }, ref) => (
    <div className="relative">
      <select ref={ref} className={cn(fieldClass, "h-10 cursor-pointer appearance-none pr-9", className)} {...props} />
      <ChevronDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
    </div>
  ),
);

/**
 * Uzbek phone field: typing `901234567` shows `+998 90 123 45 67`; pasting any common format works too.
 * Deleting every digit empties the field (so optional phones can be cleared). Validation is done by the form.
 */
export const PhoneInput = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ onChange, ...props }, ref) => (
    <Input
      ref={ref}
      type="tel"
      inputMode="tel"
      autoComplete="off"
      placeholder="+998 90 123 45 67"
      maxLength={17}
      onChange={(e) => {
        const compact = e.target.value.replace(/\s/g, "");
        // "+", "+9", "+99", "+998": the user is still typing the country code; leave it as typed.
        if (compact.startsWith("+") && "+998".startsWith(compact)) e.target.value = compact;
        else e.target.value = nationalDigits(e.target.value) ? formatPhoneInput(e.target.value) : "";
        onChange?.(e);
      }}
      {...props}
    />
  ),
);
PhoneInput.displayName = "PhoneInput";
Select.displayName = "Select";

/**
 * Whole-sum amount field: shows "150 000" while typing and keeps the caret in place; reports a number,
 * or `null` when empty. [suffix] is the translated currency label shown inside the field.
 */
export const MoneyInput = React.forwardRef<
  HTMLInputElement,
  Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange"> & {
    value: number | null | undefined;
    onValueChange: (value: number | null) => void;
    suffix?: string;
  }
>(({ value, onValueChange, suffix, className, ...props }, ref) => (
  <div className="relative">
    <Input
      ref={ref}
      inputMode="numeric"
      autoComplete="off"
      value={value === null || value === undefined ? "" : groupDigits(String(value))}
      onChange={(e) => {
        const input = e.target;
        const digitsBefore = input.value.slice(0, input.selectionStart ?? input.value.length).replace(/\D/g, "").length;
        const next = parseAmount(input.value);
        // Guard against absurd pastes; the server validates the real limit.
        if (next !== null && next > Number.MAX_SAFE_INTEGER) return;
        onValueChange(next);
        const formatted = next === null ? "" : groupDigits(String(next));
        requestAnimationFrame(() => {
          if (document.activeElement !== input) return;
          const caret = caretAfterDigits(formatted, digitsBefore);
          input.setSelectionRange(caret, caret);
        });
      }}
      className={cn("text-right tabular-nums", suffix && "pr-14", className)}
      {...props}
    />
    {suffix && (
      <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">{suffix}</span>
    )}
  </div>
));
MoneyInput.displayName = "MoneyInput";

export function Label({ className, ...props }: React.LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn("text-sm font-medium text-foreground", className)} {...props} />;
}
