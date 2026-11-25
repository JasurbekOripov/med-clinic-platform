import { todayInTashkent } from "@medclinic/web-ui/lib/format";

/** Full years on today's Tashkent date (birthdays count from the day itself). */
export function ageOn(birthDate: string, today: string = todayInTashkent()): number {
  const [by, bm, bd] = birthDate.split("-").map(Number);
  const [ty, tm, td] = today.split("-").map(Number);
  let age = ty! - by!;
  if (tm! < bm! || (tm === bm && td! < bd!)) age--;
  return Math.max(0, age);
}
