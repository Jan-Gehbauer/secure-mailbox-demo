import { defineConfig } from 'cypress';

export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:4200',
    // Diese Tests laufen gegen die ECHTE Anwendung (Frontend + Backend
    // müssen laufen, z.B. via `ng serve` + Backend in IntelliJ, oder via
    // `docker compose up`) - im Gegensatz zu den Unit-Tests, die alles
    // mocken. Das ist bewusst so: Cypress soll den kompletten, echten
    // End-to-End-Fluss inkl. Verschlüsselung/Backend/DB abdecken.
    supportFile: 'cypress/support/e2e.ts',
    specPattern: 'cypress/e2e/**/*.cy.ts'
  }
});
