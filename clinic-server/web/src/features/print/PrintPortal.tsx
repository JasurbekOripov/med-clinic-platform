import * as React from "react";
import { createPortal } from "react-dom";

/** Renders [children] only for printing (hidden on screen); see `printDocument`. */
export function PrintPortal({ children }: { children: React.ReactNode }) {
  const [root, setRoot] = React.useState<HTMLElement | null>(null);
  React.useEffect(() => {
    let el = document.getElementById("print-root");
    if (!el) {
      el = document.createElement("div");
      el.id = "print-root";
      document.body.appendChild(el);
    }
    setRoot(el);
  }, []);
  return root ? createPortal(children, root) : null;
}
