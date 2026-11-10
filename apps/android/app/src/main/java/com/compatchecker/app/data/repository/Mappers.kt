package com.compatchecker.app.data.repository

import com.compatchecker.app.data.remote.CategoryDto
import com.compatchecker.app.data.remote.CheckedRuleDto
import com.compatchecker.app.data.remote.CollectionDetailDto
import com.compatchecker.app.data.remote.CollectionDto
import com.compatchecker.app.data.remote.CollectionItemDto
import com.compatchecker.app.data.remote.CommentDto
import com.compatchecker.app.data.remote.CompatibilityResultDto
import com.compatchecker.app.data.remote.ContributionDto
import com.compatchecker.app.data.remote.LeaderboardEntryDto
import com.compatchecker.app.data.remote.PublicProfileDto
import com.compatchecker.app.data.remote.EvidenceDto
import com.compatchecker.app.data.remote.GuideDetailDto
import com.compatchecker.app.data.remote.GuideDto
import com.compatchecker.app.data.remote.GuideStepDto
import com.compatchecker.app.data.remote.MediaDto
import com.compatchecker.app.data.remote.MeDto
import com.compatchecker.app.data.remote.NotificationDto
import com.compatchecker.app.data.remote.ProductDetailDto
import com.compatchecker.app.data.remote.ProductDto
import com.compatchecker.app.data.remote.ProductRuleDto
import com.compatchecker.app.data.remote.ProductSummaryDto
import com.compatchecker.app.data.remote.ReputationDto
import com.compatchecker.app.data.remote.RequestDto
import com.compatchecker.app.data.remote.RuleDetailDto
import com.compatchecker.app.data.remote.SpecDto
import com.compatchecker.app.domain.model.AppNotification
import com.compatchecker.app.domain.model.Brand
import com.compatchecker.app.domain.model.Category
import com.compatchecker.app.domain.model.CheckedRule
import com.compatchecker.app.domain.model.CollectionDetail
import com.compatchecker.app.domain.model.CollectionItem
import com.compatchecker.app.domain.model.CollectionSummary
import com.compatchecker.app.domain.model.Comment
import com.compatchecker.app.domain.model.CommunityRequest
import com.compatchecker.app.domain.model.CompatibilityResult
import com.compatchecker.app.domain.model.Contribution
import com.compatchecker.app.domain.model.LeaderboardEntry
import com.compatchecker.app.domain.model.PublicProfile
import com.compatchecker.app.domain.model.RecentContribution
import com.compatchecker.app.domain.model.Evidence
import com.compatchecker.app.domain.model.GuideDetail
import com.compatchecker.app.domain.model.GuideLink
import com.compatchecker.app.domain.model.GuideStep
import com.compatchecker.app.domain.model.GuideSummary
import com.compatchecker.app.domain.model.MediaItem
import com.compatchecker.app.domain.model.ProductDetail
import com.compatchecker.app.domain.model.ProductRule
import com.compatchecker.app.domain.model.ProductSpec
import com.compatchecker.app.domain.model.ProductSummary
import com.compatchecker.app.domain.model.Reputation
import com.compatchecker.app.domain.model.ReputationEntry
import com.compatchecker.app.domain.model.RuleDetail
import com.compatchecker.app.domain.model.UserProfile

internal typealias UrlFor = (String?) -> String?

internal fun ProductSummaryDto.toDomain(urlFor: UrlFor): ProductSummary = ProductSummary(
    id = id,
    slug = slug,
    name = name,
    modelNumber = modelNumber,
    brandName = brandName,
    brandSlug = brandSlug,
    categoryName = categoryName,
    categorySlug = categorySlug,
    summary = summary,
    imageUrl = urlFor(imagePath),
    ruleCount = ruleCount,
)

internal fun ProductDto.toSummary(urlFor: UrlFor): ProductSummary = ProductSummary(
    id = id,
    slug = slug,
    name = name,
    modelNumber = modelNumber,
    brandName = brandName,
    brandSlug = brandSlug,
    categoryName = categoryName,
    categorySlug = categorySlug,
    summary = summary,
    imageUrl = urlFor(media.firstOrNull()?.path),
    ruleCount = 0,
)

internal fun SpecDto.toDomain() = ProductSpec(label, value, unit, isCritical)

internal fun MediaDto.toDomain(urlFor: UrlFor) = MediaItem(urlFor(path) ?: "", alt)

internal fun ProductRuleDto.toDomain() = ProductRule(
    id = id,
    relationship = relationship,
    verification = verification,
    confidence = confidence,
    riskLevel = riskLevel,
    summary = summary,
    otherProductId = otherProductId,
    otherProductName = otherProductName,
    evidenceCount = evidenceCount,
    confirmCount = confirmCount,
    disputeCount = disputeCount,
)

internal fun ProductDetailDto.toDomain(urlFor: UrlFor) = ProductDetail(
    id = id,
    slug = slug,
    name = name,
    modelNumber = modelNumber,
    releaseYear = releaseYear,
    summary = summary,
    technicalNotes = technicalNotes,
    connectorType = connectorType,
    formFactor = formFactor,
    brandName = brandName,
    brandSlug = brandSlug,
    categoryName = categoryName,
    categorySlug = categorySlug,
    aliases = aliases,
    specs = specs.map { it.toDomain() },
    media = media.map { it.toDomain(urlFor) }.filter { it.url.isNotBlank() },
    rules = rules.map { it.toDomain() },
    guides = guides.map { GuideLink(it.id, it.slug, it.title, it.summary, it.difficulty) },
    commentCount = commentCount,
    isFavorite = isFavorite,
)

internal fun EvidenceDto.toDomain() = Evidence(
    id = id,
    title = title,
    sourceUrl = sourceUrl,
    publisher = publisher,
    excerpt = excerpt,
    evidenceType = evidenceType,
    capturedAt = capturedAt,
)

internal fun CheckedRuleDto.toDomain() = CheckedRule(
    id = id,
    relationship = relationship,
    verification = verification,
    confidence = confidence,
    riskLevel = riskLevel,
    summary = summary,
    technicalDetails = technicalDetails,
    requiredModifications = requiredModifications,
    subjectProductId = subjectProductId,
    subjectProductName = subjectProductName,
    objectProductId = objectProductId,
    objectProductName = objectProductName,
    direction = direction,
    evidence = evidence.map { it.toDomain() },
    confirmCount = confirmCount,
    disputeCount = disputeCount,
)

internal fun CompatibilityResultDto.toDomain() = CompatibilityResult(
    known = known,
    outcome = outcome,
    sameProduct = sameProduct,
    ruleCount = ruleCount,
    rules = rules.map { it.toDomain() },
    hasIncompatibility = hasIncompatibility,
    checkedAt = checkedAt,
)

internal fun RuleDetailDto.toDomain() = RuleDetail(
    id = id,
    relationship = relationship,
    verification = verification,
    confidence = confidence,
    riskLevel = risk,
    summary = summary,
    technicalDetails = technicalDetails,
    requiredModifications = requiredModifications,
    subjectProductId = subjectProductId,
    subjectProductName = subjectProductName,
    subjectProductSlug = subjectProductSlug,
    subjectBrandName = subjectBrandName,
    objectProductId = objectProductId,
    objectProductName = objectProductName,
    objectProductSlug = objectProductSlug,
    objectBrandName = objectBrandName,
    evidenceCount = evidenceCount,
    confirmCount = confirmCount,
    disputeCount = disputeCount,
    myVote = myVote,
    evidence = evidence.map { it.toDomain() },
)

internal fun GuideDto.toDomain() = GuideSummary(
    id = id,
    slug = slug,
    title = title,
    summary = summary,
    bodyMarkdown = bodyMarkdown,
    difficulty = difficulty,
    productId = productId,
    productName = productName,
    authorName = authorName,
    stepCount = stepCount,
    publishedAt = publishedAt,
)

internal fun GuideStepDto.toDomain(urlFor: UrlFor) = GuideStep(
    id = id,
    position = position,
    title = title,
    body = body,
    imageUrl = urlFor(imagePath),
)

internal fun GuideDetailDto.toDomain(urlFor: UrlFor) = GuideDetail(
    guide = GuideSummary(
        id = id,
        slug = slug,
        title = title,
        summary = summary,
        bodyMarkdown = bodyMarkdown,
        difficulty = difficulty,
        productId = productId,
        productName = productName,
        authorName = authorName,
        stepCount = stepCount,
        publishedAt = publishedAt,
    ),
    steps = steps.map { it.toDomain(urlFor) },
    commentCount = commentCount,
)

internal fun CategoryDto.toDomain() = Category(id, slug, name)

internal fun RequestDto.toDomain() = CommunityRequest(
    id = id,
    title = title,
    description = description,
    status = status,
    voteCount = voteCount,
    categoryName = categoryName,
    createdAt = createdAt,
)

internal fun CommentDto.toDomain() = Comment(
    id = id,
    parentId = parentId,
    body = body,
    username = username,
    displayName = displayName,
    role = role,
    avatarUrl = avatarUrl,
    createdAt = createdAt,
)

internal fun MeDto.toDomain() = UserProfile(
    id = id,
    username = username,
    displayName = displayName,
    bio = bio,
    role = role,
    accountStatus = accountStatus,
    locale = locale,
    reputationPoints = reputationPoints,
    reputationTier = reputationTier,
    unreadNotifications = unreadNotifications,
    favoriteCount = favoriteCount,
    canContribute = capabilities.canContribute,
    canModerate = capabilities.canModerate,
    canAdmin = capabilities.canAdmin,
    deletionRequestedAt = deletionRequestedAt,
)

internal fun ReputationDto.toDomain() = Reputation(
    points = points,
    tier = tier,
    entries = entries.map { ReputationEntry(it.id, it.delta, it.reason, it.createdAt) },
)

internal fun ContributionDto.toDomain() = Contribution(
    id = id,
    kind = kind,
    status = status,
    reviewNote = reviewNote,
    createdAt = createdAt,
    reviewedAt = reviewedAt,
)

internal fun NotificationDto.toDomain() = AppNotification(
    id = id,
    type = type,
    title = title,
    body = body,
    link = link,
    readAt = readAt,
    createdAt = createdAt,
)

internal fun CollectionDto.toDomain() = CollectionSummary(
    id = id,
    title = title,
    description = description,
    isPublic = isPublic,
    itemCount = itemCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun CollectionItemDto.toDomain(urlFor: UrlFor) = CollectionItem(
    productId = productId,
    productName = productName,
    productSlug = productSlug,
    modelNumber = modelNumber,
    summary = summary,
    brandName = brandName,
    brandSlug = brandSlug,
    categoryName = categoryName,
    categorySlug = categorySlug,
    imageUrl = urlFor(imagePath),
    note = note,
    addedAt = addedAt,
)

internal fun CollectionDetailDto.toDomain(urlFor: UrlFor) = CollectionDetail(
    id = id,
    title = title,
    description = description,
    isPublic = isPublic,
    isOwner = isOwner,
    ownerUsername = ownerUsername,
    ownerDisplayName = ownerDisplayName,
    items = items.map { it.toDomain(urlFor) },
)

internal fun PublicProfileDto.toDomain(urlFor: UrlFor) = PublicProfile(
    id = id,
    username = username,
    displayName = displayName,
    bio = bio,
    avatarUrl = urlFor(avatarUrl),
    role = role,
    createdAt = createdAt,
    reputationPoints = reputationPoints,
    reputationTier = reputationTier,
    approvedContributions = approvedContributions,
    publishedRules = publishedRules,
    followers = followers,
    following = following,
    isFollowing = isFollowing,
    isSelf = isSelf,
    recentContributions = recentContributions.map {
        RecentContribution(it.kind, it.status, it.reviewedAt)
    },
)

internal fun LeaderboardEntryDto.toDomain(urlFor: UrlFor) = LeaderboardEntry(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = urlFor(avatarUrl),
    role = role,
    reputationPoints = reputationPoints,
    reputationTier = reputationTier,
    approvedContributions = approvedContributions,
)
