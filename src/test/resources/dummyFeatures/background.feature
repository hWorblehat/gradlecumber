Feature: A feature with a backstory

  Rule: backstories can succeed

    Background: backstory
      Given a precondition

    Scenario: failure
      When something happens
      Then it fails

  Rule: backstories can fail

    Background: bad backstory
      Given it fails

    Scenario: pre-failure
      Then it succeeds