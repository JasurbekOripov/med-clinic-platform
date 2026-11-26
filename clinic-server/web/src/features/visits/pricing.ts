import type { ServiceItem } from "@/api/types";

/**
 * Preview of what the server will charge, so Reception sees the total before saving. The server is the authority:
 * these mirror `ServiceDetails.priceFor` and `VisitDiscount.of` (half-up rounding to whole sum).
 */
export function priceFor(service: Pick<ServiceItem, "price" | "perDoctor" | "doctorPrices">, doctorId: string | undefined): number {
  if (!service.perDoctor || !doctorId) return service.price;
  return service.doctorPrices.find((d) => d.staffId === doctorId)?.price ?? service.price;
}

export function discountOf(price: number, percent: number | undefined): number {
  if (!percent) return 0;
  return Math.floor((price * percent + 50) / 100);
}
