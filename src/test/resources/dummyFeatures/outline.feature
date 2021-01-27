Feature: Outline

  Scenario Outline: Outline <x>
    Given a precondition
    Then <first> equals <second>

    Examples:
      | x    | first | second |
      | foo  | 12    | 12     |
      | var  | 3     | 4      |
      | baz  | 123   | 3231   |
