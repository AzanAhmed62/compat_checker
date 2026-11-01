package com.compatchecker.app.ui.checker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.compatchecker.app.core.design.CompatCheckerTheme
import com.compatchecker.app.domain.model.CompatibilityResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckerResultSummaryTest {
    @get:Rule
    val compose = createComposeRule()

    private fun result(
        outcome: String,
        known: Boolean,
        ruleCount: Int = 0,
        sameProduct: Boolean = false,
        hasIncompatibility: Boolean = false,
    ) = CompatibilityResult(
        known = known,
        outcome = outcome,
        sameProduct = sameProduct,
        ruleCount = ruleCount,
        rules = emptyList(),
        hasIncompatibility = hasIncompatibility,
        checkedAt = null,
    )

    @Test
    fun unknownResultIsExplicitlyNotGuessed() {
        compose.setContent {
            CompatCheckerTheme {
                ResultSummary(result(outcome = "unknown", known = false))
            }
        }

        compose.onNodeWithText("Unknown compatibility").assertIsDisplayed()
        compose.onNodeWithText(
            "No explicit published rule exists for this pair. Compat Checker will not guess.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Unknown: no published rule for this exact pair.").assertIsDisplayed()
    }

    @Test
    fun incompatibleResultShowsPrecedenceExplanation() {
        compose.setContent {
            CompatCheckerTheme {
                ResultSummary(
                    result(
                        outcome = "incompatible",
                        known = true,
                        ruleCount = 2,
                        hasIncompatibility = true,
                    ),
                )
            }
        }

        compose.onNodeWithText("Not compatible").assertIsDisplayed()
        compose.onNodeWithText(
            "At least one published rule says these products are not compatible. Positive claims cannot override it.",
        ).assertIsDisplayed()
        compose.onNodeWithText(
            "2 published rules were evaluated. Incompatibility takes precedence.",
        ).assertIsDisplayed()
    }

    @Test
    fun compatibleResultShowsEvidenceCaveat() {
        compose.setContent {
            CompatCheckerTheme {
                ResultSummary(result(outcome = "compatible", known = true, ruleCount = 1))
            }
        }

        compose.onNodeWithText("Compatible").assertIsDisplayed()
        compose.onNodeWithText(
            "Published rules relate these products. Review the evidence and required modifications before relying on it.",
        ).assertIsDisplayed()
        compose.onNodeWithText(
            "1 published rule was evaluated. Incompatibility takes precedence.",
        ).assertIsDisplayed()
    }

    @Test
    fun sameProductResultDoesNotShowARuleCount() {
        compose.setContent {
            CompatCheckerTheme {
                ResultSummary(
                    result(outcome = "unknown", known = false, sameProduct = true),
                )
            }
        }

        compose.onNodeWithText("Choose two different products to compare.").assertIsDisplayed()
        compose.onNodeWithText("Unknown: no published rule for this exact pair.").assertDoesNotExist()
    }
}
