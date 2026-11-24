package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUserResponse(
    val login: String,
    val name: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    val name: String,
    val description: String = "Built automatically via APK Forge",
    val private: Boolean = false,
    @Json(name = "auto_init") val autoInit: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GitHubRepoResponse(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    val private: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GitRefResponse(
    val ref: String,
    @Json(name = "object") val refObject: GitObjectRef
)

@JsonClass(generateAdapter = true)
data class GitObjectRef(
    val sha: String,
    val type: String
)

@JsonClass(generateAdapter = true)
data class GitCommitResponse(
    val sha: String,
    val tree: GitTreeSummary
)

@JsonClass(generateAdapter = true)
data class GitTreeSummary(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class PutFileContentRequest(
    val message: String,
    val content: String,
    val branch: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

@JsonClass(generateAdapter = true)
data class CreateBlobResponse(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class GitTreeEntryInput(
    val path: String,
    val mode: String,
    val type: String = "blob",
    val sha: String
)

@JsonClass(generateAdapter = true)
data class CreateTreeRequest(
    @Json(name = "base_tree") val baseTree: String? = null,
    val tree: List<GitTreeEntryInput>
)

@JsonClass(generateAdapter = true)
data class CreateTreeResponse(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class CreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String>
)

@JsonClass(generateAdapter = true)
data class CreateCommitResponse(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class UpdateRefRequest(
    val sha: String,
    val force: Boolean = true
)

@JsonClass(generateAdapter = true)
data class WorkflowDispatchRequest(
    val ref: String
)

@JsonClass(generateAdapter = true)
data class WorkflowRunsResponse(
    @Json(name = "total_count") val totalCount: Int,
    @Json(name = "workflow_runs") val workflowRuns: List<WorkflowRunItem>
)

@JsonClass(generateAdapter = true)
data class WorkflowRunItem(
    val id: Long,
    val name: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @Json(name = "head_sha") val headSha: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class WorkflowJobsResponse(
    @Json(name = "total_count") val totalCount: Int,
    val jobs: List<WorkflowJobItem>
)

@JsonClass(generateAdapter = true)
data class WorkflowJobItem(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val steps: List<WorkflowStepItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkflowStepItem(
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val number: Int
)

@JsonClass(generateAdapter = true)
data class ArtifactsResponse(
    @Json(name = "total_count") val totalCount: Int,
    val artifacts: List<ArtifactItem>
)

@JsonClass(generateAdapter = true)
data class ArtifactItem(
    val id: Long,
    val name: String,
    @Json(name = "size_in_bytes") val sizeInBytes: Long,
    @Json(name = "archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseItem(
    val id: Long,
    @Json(name = "tag_name") val tagName: String,
    val assets: List<ReleaseAssetItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ReleaseAssetItem(
    val id: Long,
    val name: String,
    val size: Long,
    @Json(name = "browser_download_url") val browserDownloadUrl: String
)
