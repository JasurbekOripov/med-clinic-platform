# @medclinic/web-ui

Source-only shared UI for `vendor-platform/web` and `clinic-server/web`: shadcn-style primitives, form field,
dialogs, API client (session + CSRF + problem+json), formatting (Asia/Tashkent), Uzbek phone input.

Apps import it as `@medclinic/web-ui/...` (Vite alias + TS path) and must:
- add `@source "../../../libs/web-ui/src";` to their CSS so Tailwind sees the classes;
- define the design tokens (`--color-primary`, …) in their own CSS — each app has its own look;
- provide `common.*` and `errors.*` translation keys (used by shared components).
