import { AlertTriangle, Check, Copy, Inbox, Loader2, Search } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { errorMessage } from "@/lib/errors";
import { cn } from "@/lib/utils";
import { Button } from "./ui/button";
import { Input } from "./ui/input";

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: React.ReactNode; actions?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <div className="mt-1 text-sm text-muted-foreground">{subtitle}</div>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

/**
 * Keyboard-first search: focused on page load; Enter submits (callers may open the first result);
 * Escape clears.
 */
export function SearchBox({
  value,
  onChange,
  onEnter,
  placeholder,
  autoFocus = true,
  className,
}: {
  value: string;
  onChange: (value: string) => void;
  onEnter?: () => void;
  placeholder: string;
  autoFocus?: boolean;
  className?: string;
}) {
  const { t } = useTranslation();
  return (
    <div className={cn("relative w-full max-w-md", className)}>
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
      <Input
        type="search"
        value={value}
        autoFocus={autoFocus}
        placeholder={placeholder}
        aria-label={t("common.search")}
        title={t("common.searchHint")}
        className="h-11 pl-9 text-base"
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") onEnter?.();
          if (e.key === "Escape") onChange("");
        }}
      />
    </div>
  );
}

export function Spinner({ className }: { className?: string }) {
  const { t } = useTranslation();
  return (
    <div className={cn("flex items-center justify-center gap-2 py-10 text-sm text-muted-foreground", className)} role="status">
      <Loader2 className="size-4 animate-spin" aria-hidden />
      {t("common.loading")}
    </div>
  );
}

export function EmptyState({ text, action }: { text: string; action?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 py-12 text-center text-sm text-muted-foreground">
      <Inbox className="size-8 text-slate-300" aria-hidden />
      <p>{text}</p>
      {action}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-center gap-3 py-10 text-center text-sm" role="alert">
      <AlertTriangle className="size-7 text-danger" aria-hidden />
      <p className="text-foreground">{errorMessage(t, error)}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          {t("common.retry")}
        </Button>
      )}
    </div>
  );
}

export function FormError({ error }: { error: unknown }) {
  const { t } = useTranslation();
  if (!error) return null;
  return (
    <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800" role="alert">
      {errorMessage(t, error)}
    </div>
  );
}

export function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  const { t } = useTranslation();
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-end gap-3 px-4 py-3 text-sm text-muted-foreground">
      <span>{t("common.pageOf", { page: page + 1, total: totalPages })}</span>
      <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
        {t("common.previous")}
      </Button>
      <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        {t("common.next")}
      </Button>
    </div>
  );
}

export function CopyButton({ value, className }: { value: string; className?: string }) {
  const { t } = useTranslation();
  const [copied, setCopied] = React.useState(false);
  return (
    <Button
      variant="secondary"
      size="sm"
      className={className}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        } catch {
          /* clipboard blocked (http/permissions): the value is still visible and selectable */
        }
      }}
    >
      {copied ? <Check /> : <Copy />}
      {copied ? t("common.copied") : t("common.copy")}
    </Button>
  );
}

/** Small segmented filter (status chips) — large, colour-neutral, keyboard reachable. */
export function Segmented<T extends string>({
  value,
  options,
  onChange,
}: {
  value: T;
  options: { value: T; label: string; count?: number }[];
  onChange: (value: T) => void;
}) {
  return (
    <div className="inline-flex flex-wrap gap-1 rounded-lg border border-border bg-surface p-1" role="tablist">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="tab"
          aria-selected={value === o.value}
          onClick={() => onChange(o.value)}
          className={cn(
            "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
            value === o.value ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted",
          )}
        >
          {o.label}
          {o.count !== undefined && <span className="ml-1.5 opacity-75">{o.count}</span>}
        </button>
      ))}
    </div>
  );
}
