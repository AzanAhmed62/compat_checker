package com.compatchecker.app.data.repository

import com.compatchecker.app.data.remote.ContributionDto
import com.compatchecker.app.data.remote.ContributionsDto
import com.compatchecker.app.data.remote.DeletionResultDto
import com.compatchecker.app.data.remote.MeDto
import com.compatchecker.app.data.remote.NotificationDto
import com.compatchecker.app.data.remote.NotificationsDto
import com.compatchecker.app.data.remote.ReputationDto
import com.compatchecker.app.data.remote.SupabaseGateway
import com.compatchecker.app.domain.model.AppNotification
import com.compatchecker.app.domain.model.Contribution
import com.compatchecker.app.domain.model.Reputation
import com.compatchecker.app.domain.model.UserProfile
import com.compatchecker.app.domain.repository.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProfileRepositoryImpl(private val gateway: SupabaseGateway?) : ProfileRepository {
    override suspend fun me(): UserProfile {
        val remote = requireGateway()
        val dto = remote.client.postgrest.rpc("me", buildJsonObject {})
            .decodeAs<MeDto?>()
            ?: error("Your profile is not available.")
        return dto.toDomain()
    }

    override suspend fun updateProfile(displayName: String?, bio: String?, locale: String?): UserProfile {
        val remote = requireGateway()
        val parameters = buildJsonObject {
            if (displayName != null) put("p_display_name", displayName.trim())
            if (bio != null) put("p_bio", bio.trim())
            if (locale != null) put("p_locale", locale)
        }
        remote.client.postgrest.rpc("update_my_profile", parameters).decodeAs<JsonElement>()
        return me()
    }

    override suspend fun reputation(): Reputation {
        val remote = requireGateway()
        val dto = remote.client.postgrest.rpc("my_reputation", buildJsonObject {})
            .decodeAs<ReputationDto>()
        return dto.toDomain()
    }

    override suspend fun contributions(): List<Contribution> {
        val remote = requireGateway()
        val result = remote.client.postgrest.rpc("list_my_contributions", buildJsonObject {})
            .decodeAs<ContributionsDto>()
        return result.items.map(ContributionDto::toDomain)
    }

    override suspend fun submitContribution(kind: String, payload: JsonObject): String {
        val remote = requireGateway()
        return remote.client.postgrest.rpc(
            "submit_contribution",
            buildJsonObject {
                put("p_kind", kind)
                put("p_payload", payload)
            },
        ).decodeAs<String>()
    }

    override suspend fun submitProductCorrection(productId: String, patch: JsonObject): String =
        submitContribution(
            "product_edit",
            buildJsonObject {
                put("product_id", productId)
                put("patch", patch)
            },
        )

    override suspend fun notifications(): List<AppNotification> {
        val remote = requireGateway()
        val result = remote.client.postgrest.rpc(
            "list_my_notifications",
            buildJsonObject {
                put("p_limit", 100)
                put("p_offset", 0)
            },
        ).decodeAs<NotificationsDto>()
        return result.items.map(NotificationDto::toDomain)
    }

    override suspend fun markNotificationRead(id: String) {
        val remote = requireGateway()
        remote.client.postgrest.rpc(
            "mark_notification_read",
            buildJsonObject { put("p_notification_id", id) },
        ).decodeAs<JsonElement>()
    }

    override suspend fun markAllNotificationsRead() {
        val remote = requireGateway()
        remote.client.postgrest.rpc("mark_all_notifications_read", buildJsonObject {})
            .decodeAs<Int>()
    }

    override suspend fun requestAccountDeletion(): String {
        val remote = requireGateway()
        val result = remote.client.postgrest.rpc("request_account_deletion", buildJsonObject {})
            .decodeAs<DeletionResultDto>()
        return result.message ?: "Your account is scheduled for deletion."
    }

    override suspend fun cancelAccountDeletion() {
        val remote = requireGateway()
        remote.client.postgrest.rpc("cancel_account_deletion", buildJsonObject {})
            .decodeAs<Boolean>()
    }

    private fun requireGateway(): SupabaseGateway =
        gateway ?: error("Compat Checker is not configured.")
}
