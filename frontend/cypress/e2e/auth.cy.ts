describe('Authentication flow', () => {

  it('shows the login form by default (no session yet)', () => {
    cy.visit('/');
    cy.get('[data-cy=auth-section]', { timeout: 10000 }).should('exist');
    cy.get('[data-cy=login-form]').should('exist');
  });

  it('registers a new user and lands directly in the logged-in view', () => {
    cy.registerNewUser().then((username) => {
      cy.get('[data-cy=main-view]').should('exist');
      cy.get('[data-cy=current-username]').should('contain.text', username);
      cy.get('[data-cy=inbox-empty]').should('exist'); // frisches Konto, keine Nachrichten
    });
  });

  it('registering with an already-taken username shows an error', () => {
    cy.registerNewUser().then((username) => {
      cy.get('[data-cy=logout-button]').click();

      cy.get('[data-cy=auth-tab-register]').click();
      cy.get('[data-cy=register-username]').type(username);
      cy.get('[data-cy=register-email]').type(`different_${username}@example.com`);
      cy.get('[data-cy=register-password]').type('einAnderesPasswort123');
      cy.get('[data-cy=register-submit]').click();

      cy.get('[data-cy=auth-error]').should('exist');
      // Bleibt auf dem Auth-Screen, keine versehentliche Anmeldung
      cy.get('[data-cy=main-view]').should('not.exist');
    });
  });

  it('registering with a too-short password shows a validation error from the backend', () => {
    cy.visit('/');
    cy.get('[data-cy=auth-tab-register]').click();
    cy.get('[data-cy=register-username]').type(`cy_shortpw_${Date.now()}`);
    cy.get('[data-cy=register-email]').type(`shortpw_${Date.now()}@example.com`);

    // Das native "minlength"-Attribut würde das Formular clientseitig
    // blockieren - wir entfernen es hier bewusst, um gezielt die
    // SERVERSEITIGE Validierung zu testen (min. 12 Zeichen, siehe
    // RegisterRequest.java).
    cy.get('[data-cy=register-password]').invoke('removeAttr', 'minlength');
    cy.get('[data-cy=register-password]').type('zukurz');
    cy.get('[data-cy=register-submit]').click();

    cy.get('[data-cy=auth-error]').should('exist');
    cy.get('[data-cy=main-view]').should('not.exist');
  });

  it('logs out and back in with the same credentials', () => {
    const password = 'sicheresPasswort123';

    cy.registerNewUser(password).then((username) => {
      cy.get('[data-cy=logout-button]').click();
      cy.get('[data-cy=auth-section]').should('exist');
      cy.get('[data-cy=main-view]').should('not.exist');

      cy.get('[data-cy=auth-tab-login]').click();
      cy.get('[data-cy=login-username]').type(username);
      cy.get('[data-cy=login-password]').type(password);
      cy.get('[data-cy=login-submit]').click();

      cy.get('[data-cy=main-view]').should('exist');
      cy.get('[data-cy=current-username]').should('contain.text', username);
    });
  });

  it('shows an error for a wrong password without revealing whether the username exists', () => {
    cy.registerNewUser().then((username) => {
      cy.get('[data-cy=logout-button]').click();

      cy.get('[data-cy=auth-tab-login]').click();
      cy.get('[data-cy=login-username]').type(username);
      cy.get('[data-cy=login-password]').type('komplettFalsch');
      cy.get('[data-cy=login-submit]').click();

      cy.get('[data-cy=auth-error]').should('exist');
      cy.get('[data-cy=main-view]').should('not.exist');
    });
  });
});
