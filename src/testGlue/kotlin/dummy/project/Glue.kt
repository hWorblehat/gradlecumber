package dummy.project

import io.cucumber.datatable.DataTable
import io.cucumber.java8.En
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe

class Glue : En {
	init {

		Given("a precondition") {}

		When("something happens") {}

		Then("it succeeds") {}

		Then("it fails") {
			fail("It failed!")
		}

		Then("{int} equals {int}") { a: Int, b: Int ->
			a shouldBe b
		}

		Then("this table fails:") { data: DataTable ->
			fail("The table fell over!")
		}

	}
}