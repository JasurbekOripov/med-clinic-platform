/**
 * Prints one document from the page: the document is rendered into `#print-root` (see `PrintPortal`), everything else
 * is hidden by print CSS, and the paper size is set for this print only (80 mm receipt roll or A4).
 * The browser's print dialog lets staff pick the printer; silent printing needs the Print Agent (later).
 */
export type PaperSize = "receipt80" | "a4";

const PAGE_CSS: Record<PaperSize, string> = {
  receipt80: "@page { size: 80mm auto; margin: 2mm 3mm; }",
  a4: "@page { size: A4; margin: 15mm; }",
};

export function printDocument(paper: PaperSize): void {
  const style = document.createElement("style");
  style.dataset.print = paper;
  style.textContent = PAGE_CSS[paper];
  document.head.appendChild(style);
  document.body.classList.add("printing");
  const cleanup = () => {
    style.remove();
    document.body.classList.remove("printing");
    window.removeEventListener("afterprint", cleanup);
  };
  window.addEventListener("afterprint", cleanup);
  window.print();
  // Some browsers return from print() only after the dialog closes but never fire afterprint.
  setTimeout(cleanup, 1000);
}
