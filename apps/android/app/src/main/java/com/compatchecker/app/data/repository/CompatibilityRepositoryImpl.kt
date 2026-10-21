package com.compatchecker.app.data.repository

import com.compatchecker.app.data.remote.CompatibilityResultDto
import com.compatchecker.app.data.remote.RuleDetailDto
import com.compatchecker.app.data.remote.SupabaseGateway
import com.compatchecker.app.domain.model.CompatibilityResult
import com.compatchecker.app.domain.model.RuleDetail
import com.compatchecker.app.domain.repository.CompatibilityRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CompatibilityRepositoryImpl(private val gateway: SupabaseGateway?) : CompatibilityRepository {
    override suspend fun check(productA: String, productB: String): CompatibilityResult {
        val remote = requireGateway()
        val parameters = buildJsonObject {
            put("p_product_a", productA)
            put("p_product_b", productB)
        }
        val dto = remote.client.postgrest.rpc("check_compatibility", parameters)
            .decodeAs<CompatibilityResultDto>()
        return dto.toDomain()
    }

    override suspend fun rule(ruleId: String): RuleDetail {
        val remote = requireGateway()
        val dto = remote.client.postgrest
            .rpc("get_rule_detail", buildJsonObject { put("p_rule_id", ruleId) })
            .decodeAs<RuleDetailDto?>()
            ?: error("Rule not found.")
        return dto.toDomain()
    }

    override suspend fun castVote(ruleId: String, vote: String): RuleDetail {
        val remote = requireGateway()
        remote.client.postgrest
            .rpc(
                "cast_rule_vote",
                buildJsonObject {
                    put("p_rule_id", ruleId)
                    put("p_vote", vote)
                },
            )
            .decodeAs<JsonElement>()
        return rule(ruleId)
    }

    private fun requireGateway(): SupabaseGateway =
        gateway ?: error("Compat Checker is not configured.")
}
