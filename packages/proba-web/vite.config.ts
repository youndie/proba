import { defineConfig } from "vite";

// Next.js rewrites tsconfig.json on its first run and sets `jsx: preserve`, because it does the
// transform itself. The preview script does not go through Next, so it says here what tsconfig no
// longer says for it.
export default defineConfig({
  esbuild: { jsx: "automatic" },
});
