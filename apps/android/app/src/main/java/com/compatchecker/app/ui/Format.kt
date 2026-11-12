package com.compatchecker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.compatchecker.app.R
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun Throwable.displayMessage(): String = when (this) {
    is UnknownHostException, is ConnectException, is SocketTimeoutException ->
        "Cannot reach the server. Check your connection and try again."
    is IOException -> "Network error. Cached content may be available."
    else -> message
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(280)
        ?.ifBlank { null }
        ?: "Something went wrong. Please try again."
}

fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(value).format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault()),
        )
    }.getOrElse {
        value.take(10)
    }
}

@Composable
fun relationshipLabel(value: String): String = when (value) {
    "fits" -> stringResource(R.string.relationship_fits)
    "works_with" -> stringResource(R.string.relationship_works_with)
    "same_as" -> stringResource(R.string.relationship_same_as)
    "variant_of" -> stringResource(R.string.relationship_variant_of)
    "not_compatible_with" -> stringResource(R.string.relationship_not_compatible)
    else -> humanize(value)
}

@Composable
fun verificationLabel(value: String): String = when (value) {
    "official" -> stringResource(R.string.verification_official)
    "community_verified" -> stringResource(R.string.verification_community_verified)
    "needs_verification" -> stringResource(R.string.verification_needs_verification)
    "experimental" -> stringResource(R.string.verification_experimental)
    else -> humanize(value)
}

@Composable
fun difficultyLabel(value: String): String = when (value) {
    "easy" -> stringResource(R.string.difficulty_easy)
    "moderate" -> stringResource(R.string.difficulty_moderate)
    "difficult" -> stringResource(R.string.difficulty_difficult)
    "expert" -> stringResource(R.string.difficulty_expert)
    else -> humanize(value)
}

@Composable
fun statusLabel(value: String): String = when (value) {
    "active" -> stringResource(R.string.status_active)
    "suspended" -> stringResource(R.string.status_suspended)
    "banned" -> stringResource(R.string.status_banned)
    "deletion_pending" -> stringResource(R.string.status_deletion_pending)
    "deleted" -> stringResource(R.string.status_deleted)
    "draft" -> stringResource(R.string.status_draft)
    "pending_review" -> stringResource(R.string.status_pending_review)
    "published" -> stringResource(R.string.status_published)
    "archived" -> stringResource(R.string.status_archived)
    "retired" -> stringResource(R.string.status_retired)
    "pending" -> stringResource(R.string.status_pending)
    "needs_changes" -> stringResource(R.string.status_needs_changes)
    "approved" -> stringResource(R.string.status_approved)
    "rejected" -> stringResource(R.string.status_rejected)
    "withdrawn" -> stringResource(R.string.status_withdrawn)
    "open" -> stringResource(R.string.status_open)
    "planned" -> stringResource(R.string.status_planned)
    "fulfilled" -> stringResource(R.string.status_fulfilled)
    "declined" -> stringResource(R.string.status_declined)
    "admin" -> stringResource(R.string.role_admin)
    "moderator" -> stringResource(R.string.role_moderator)
    "member" -> stringResource(R.string.role_member)
    "trusted" -> stringResource(R.string.role_trusted)
    "contributor" -> stringResource(R.string.role_contributor)
    "newcomer" -> stringResource(R.string.role_newcomer)
    "visitor" -> stringResource(R.string.role_visitor)
    "new_product" -> stringResource(R.string.contribution_kind_new_product)
    "product_edit" -> stringResource(R.string.contribution_kind_product_edit)
    "new_rule" -> stringResource(R.string.contribution_kind_new_rule)
    "evidence" -> stringResource(R.string.contribution_kind_evidence)
    "guide" -> stringResource(R.string.contribution_kind_guide)
    else -> humanize(value)
}

private fun humanize(value: String): String =
    value.replace('_', ' ').replaceFirstChar { it.uppercase() }
