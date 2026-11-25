import { cva, type VariantProps } from "class-variance-authority";
import type * as React from "react";
import { cn } from "@medclinic/web-ui/lib/utils";

export const badgeVariants = cva("inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap", {
  variants: {
    tone: {
      green: "bg-emerald-100 text-emerald-800",
      red: "bg-red-100 text-red-800",
      amber: "bg-amber-100 text-amber-900",
      blue: "bg-sky-100 text-sky-800",
      gray: "bg-slate-100 text-slate-700",
    },
  },
  defaultVariants: { tone: "gray" },
});

export type BadgeTone = NonNullable<VariantProps<typeof badgeVariants>["tone"]>;

const DOT: Record<BadgeTone, string> = {
  green: "bg-emerald-500",
  red: "bg-red-500",
  amber: "bg-amber-500",
  blue: "bg-sky-500",
  gray: "bg-slate-400",
};

export function Badge({ tone = "gray", className, children, ...props }: React.HTMLAttributes<HTMLSpanElement> & { tone?: BadgeTone }) {
  return (
    <span className={cn(badgeVariants({ tone }), className)} {...props}>
      <span className={cn("size-1.5 rounded-full", DOT[tone])} aria-hidden />
      {children}
    </span>
  );
}
