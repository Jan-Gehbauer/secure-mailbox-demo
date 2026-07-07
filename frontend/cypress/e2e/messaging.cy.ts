describe('Messaging', () => {

  it('sends a message to yourself and sees it appear in the inbox', () => {
    cy.registerNewUser().then((username) => {
      const subject = `Testbetreff ${Date.now()}`;
      const body = 'Dies ist eine Ende-zu-Ende-Testnachricht mit Umlauten: äöü.';

      cy.get('[data-cy=message-recipient]').type(username);
      cy.get('[data-cy=message-subject]').type(subject);
      cy.get('[data-cy=message-body]').type(body);
      cy.get('[data-cy=send-message-submit]').click();

      cy.get('[data-cy=send-success]').should('exist');

      // Nachricht muss automatisch im Posteingang auftauchen (kein
      // manuelles "Aktualisieren" noetig - das war ein Bug, den wir
      // frueher im Projekt gefixt haben, siehe Signals-Umstellung)
      cy.get('[data-cy=inbox-item]', { timeout: 10000 }).should('have.length.at.least', 1);
      cy.get('[data-cy=inbox-item-subject]').first().should('contain.text', subject);
      cy.get('[data-cy=inbox-item-sender]').first().should('contain.text', username);
      cy.get('[data-cy=inbox-item-body]').first().should('contain.text', body);
    });
  });

  it('shows an error when sending to a recipient that does not exist', () => {
    cy.registerNewUser().then(() => {
      cy.get('[data-cy=message-recipient]').type('dieser_nutzer_existiert_ganz_sicher_nicht');
      cy.get('[data-cy=message-subject]').type('Betreff');
      cy.get('[data-cy=message-body]').type('Inhalt');
      cy.get('[data-cy=send-message-submit]').click();

      cy.get('[data-cy=general-error]').should('exist');
      cy.get('[data-cy=send-success]').should('not.exist');
    });
  });

  it('the inbox stays empty for a user who has not received anything', () => {
    cy.registerNewUser().then(() => {
      cy.get('[data-cy=inbox-empty]').should('exist');
      cy.get('[data-cy=inbox-item]').should('not.exist');
    });
  });

  it('the refresh button reloads the inbox on demand', () => {
    cy.registerNewUser().then(() => {
      cy.get('[data-cy=refresh-inbox]').click();
      cy.get('[data-cy=inbox-empty]').should('exist');
    });
  });
});
