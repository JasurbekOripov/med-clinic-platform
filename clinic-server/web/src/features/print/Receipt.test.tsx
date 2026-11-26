import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Payment, PrintSettings } from "@/api/types";
import { Receipt } from "./Receipt";

// Testing Library normalises whitespace, so grouped amounts ("150 000" with a narrow no-break space) match plain spaces.

const payment: Payment = {
  id: "p1",
  receiptNo: 42,
  visitId: "v1",
  visitNo: 7,
  patient: { id: "pt", cardNo: 15, fullName: "Karimova Dilnoza" },
  amount: 196000,
  method: "CASH",
  cashierName: "Aliyeva Malika",
  lines: [
    { serviceName: "Kardiolog", doctorName: "Rahimov A.", price: 200000, discount: 40000, amount: 160000 },
    { serviceName: "Qon tahlili", price: 45000, discount: 9000, amount: 36000 },
  ],
  createdAt: "2026-11-26T05:30:00Z",
};

const clinic: PrintSettings = { name: "Shifo Med", address: "Toshkent, Chilonzor 5", receiptFooter: "Rahmat!", hasLogo: false };

describe("Receipt", () => {
  it("prints clinic, patient, every line with its discount and the total", () => {
    render(<Receipt payment={payment} clinic={clinic} />);
    expect(screen.getByText("Shifo Med")).toBeInTheDocument();
    expect(screen.getByText("Toshkent, Chilonzor 5")).toBeInTheDocument();
    expect(screen.getByText(/Karimova Dilnoza \(15\)/)).toBeInTheDocument();
    expect(screen.getByText("Rahimov A.")).toBeInTheDocument();
    expect(screen.getByText("200 000 − 40 000")).toBeInTheDocument();
    expect(screen.getByText("49 000")).toBeInTheDocument(); // total discount
    expect(screen.getByText(/196 000/)).toBeInTheDocument();
    expect(screen.getByText("Rahmat!")).toBeInTheDocument();
    expect(screen.getByText("Aliyeva Malika")).toBeInTheDocument();
  });
});
