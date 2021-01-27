Feature: A feature with rules

  Rule: Rules should be included

    Scenario: This should pass
      Given a precondition
      When something happens
      Then it succeeds

    Scenario: This should fail
      Given a precondition
      When something happens
      Then it fails

  Rule: Multiple rules are allowed

    Scenario: This is unimplemented
      Given an unimplemented precondition
      Then something doesn't happen