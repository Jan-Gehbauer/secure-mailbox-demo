/**
 * Registriert einen frischen, eindeutigen Test-Nutzer über die UI (nicht
 * per API-Call) - so testen wir den kompletten Weg durch die Anwendung,
 * inklusive Formular, Validierung und automatischem Einloggen danach.
 *
 * Gibt den generierten Benutzernamen zurück, damit Tests ihn z.B. als
 * Empfänger für eine Nachricht an sich selbst verwenden können.
 */
Cypress.Commands.add('registerNewUser', (password: string = 'sicheresPasswort123') => {
  const username = `cy_user_${Date.now()}_${Math.floor(Math.random() * 10000)}`;

  cy.visit('/');
  cy.get('[data-cy=auth-tab-register]').click();
  cy.get('[data-cy=register-username]').type(username);
  cy.get('[data-cy=register-email]').type(`${username}@example.com`);
  cy.get('[data-cy=register-password]').type(password);
  cy.get('[data-cy=register-submit]').click();

  // Nach erfolgreicher Registrierung ist man automatisch eingeloggt
  cy.get('[data-cy=main-view]', { timeout: 10000 }).should('exist');
  cy.get('[data-cy=current-username]').should('contain.text', username);

  return cy.wrap(username);
});

declare global {
  namespace Cypress {
    interface Chainable {
      registerNewUser(password?: string): Chainable<string>;
    }
  }
}

export {};
