package dummy.project

import io.cucumber.java8.En
import io.kotest.assertions.fail

class GlueWithFailingHooks : En {
	init {
		Before("@hook") { _ ->
			fail("We failed before we even got started :(")
		}

		When("pigs fly") {}
		Then("I will truly be amazed") {}
	}
}