import { BookmarkPlus, FileText, Trash2 } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { FormError } from "@medclinic/web-ui/components/Common";
import { Field } from "@medclinic/web-ui/components/Field";
import { Button } from "@medclinic/web-ui/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogTitle } from "@medclinic/web-ui/components/ui/dialog";
import { Input, Select, Textarea } from "@medclinic/web-ui/components/ui/input";
import { errorMessage, fieldErrors } from "@medclinic/web-ui/lib/errors";
import { useConclusionTemplateMutations, useConclusionTemplates, useSaveConclusion } from "@/api/hooks";
import type { Conclusion } from "@/api/types";

/**
 * The doctor's text: the printed header, patient data and signature are added automatically, so this is only the
 * title and the free text. Own templates are inserted at the cursor.
 */
export function ConclusionEditor({
  conclusion,
  visitItemId,
  defaultTitle,
  onSaved,
  onClose,
}: {
  conclusion?: Conclusion;
  visitItemId?: string;
  defaultTitle?: string;
  onSaved: (c: Conclusion) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const save = useSaveConclusion();
  const templates = useConclusionTemplates();
  const { save: saveTemplate, remove: removeTemplate } = useConclusionTemplateMutations();
  const [title, setTitle] = React.useState(conclusion?.title ?? defaultTitle ?? "");
  const [body, setBody] = React.useState(conclusion?.body ?? "");
  const [templateName, setTemplateName] = React.useState<string | null>(null);
  const area = React.useRef<HTMLTextAreaElement>(null);
  const server = fieldErrors(t, save.error);

  const insert = (text: string) => {
    const el = area.current;
    const at = el?.selectionStart ?? body.length;
    const end = el?.selectionEnd ?? at;
    const next = body.slice(0, at) + text + body.slice(end);
    setBody(next);
    requestAnimationFrame(() => {
      el?.focus();
      el?.setSelectionRange(at + text.length, at + text.length);
    });
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent wide className="max-w-4xl">
        <DialogTitle>{conclusion ? t("conclusions.edit") : t("conclusions.write")}</DialogTitle>
        <form
          className="mt-5 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate(
              conclusion ? { id: conclusion.id, title, body, version: conclusion.version } : { visitItemId, title, body },
              { onSuccess: (c) => { toast.success(t("common.saved")); onSaved(c); } },
            );
          }}
        >
          <Field label={t("conclusions.title")} error={server.title}>
            <Input value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} className="text-base font-medium" />
          </Field>

          <div className="flex flex-wrap items-center gap-2">
            <FileText className="size-4 text-muted-foreground" aria-hidden />
            <Select
              aria-label={t("conclusions.insertTemplate")}
              value=""
              className="h-9 w-64 text-sm"
              onChange={(e) => {
                const tpl = templates.data?.find((x) => x.id === e.target.value);
                if (tpl) insert(tpl.body);
              }}
            >
              <option value="">{templates.data?.length ? t("conclusions.insertTemplate") : t("conclusions.noTemplates")}</option>
              {(templates.data ?? []).map((x) => (
                <option key={x.id} value={x.id}>{x.name}</option>
              ))}
            </Select>
            {templateName === null ? (
              <Button type="button" size="sm" variant="ghost" disabled={!body.trim()} onClick={() => setTemplateName("")}>
                <BookmarkPlus /> {t("conclusions.saveAsTemplate")}
              </Button>
            ) : (
              <span className="flex items-center gap-2">
                <Input
                  autoFocus
                  value={templateName}
                  maxLength={100}
                  placeholder={t("conclusions.templateName")}
                  aria-label={t("conclusions.templateName")}
                  onChange={(e) => setTemplateName(e.target.value)}
                  className="h-9 w-48 text-sm"
                />
                <Button
                  type="button"
                  size="sm"
                  disabled={!templateName.trim() || saveTemplate.isPending}
                  onClick={() =>
                    saveTemplate.mutate(
                      { body: { name: templateName, body } },
                      {
                        onSuccess: () => { toast.success(t("conclusions.templateSaved")); setTemplateName(null); },
                        onError: (err) => toast.error(errorMessage(t, err)),
                      },
                    )
                  }
                >
                  {t("common.save")}
                </Button>
                <Button type="button" size="sm" variant="ghost" onClick={() => setTemplateName(null)}>{t("common.cancel")}</Button>
              </span>
            )}
          </div>

          <Field label={t("conclusions.body")} error={server.body} hint={t("conclusions.bodyHint")}>
            <Textarea ref={area} value={body} onChange={(e) => setBody(e.target.value)} className="min-h-[22rem] font-serif text-base leading-relaxed" />
          </Field>

          {(templates.data?.length ?? 0) > 0 && (
            <details className="text-sm">
              <summary className="cursor-pointer text-muted-foreground">{t("conclusions.manageTemplates")}</summary>
              <ul className="mt-2 divide-y divide-border rounded-lg border border-border">
                {templates.data!.map((x) => (
                  <li key={x.id} className="flex items-center gap-2 px-3 py-1.5">
                    <span className="flex-1">{x.name}</span>
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      aria-label={t("common.delete")}
                      onClick={() => removeTemplate.mutate({ id: x.id, version: x.version }, { onError: (err) => toast.error(errorMessage(t, err)) })}
                    >
                      <Trash2 />
                    </Button>
                  </li>
                ))}
              </ul>
            </details>
          )}

          <FormError error={save.error} />
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>{t("common.cancel")}</Button>
            <Button type="submit" size="lg" disabled={save.isPending || !title.trim() || !body.trim()}>{t("common.save")}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
