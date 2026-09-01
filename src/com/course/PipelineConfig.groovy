package com.course

/**
 * PipelineConfig — config-as-data (AD-15).
 * Membaca .cicd/pipeline.yaml dari repo app, menerapkan default, dan memvalidasi field wajib.
 * Mengganti pola lama "27 env var dengan cek null".
 */
class PipelineConfig implements Serializable {

    String appName
    String language
    String testCommand

    String registryRegion
    String registryProject
    String registryRepository

    String gitopsRepoUrl
    String gitopsBranch
    String gitopsPath
    String gitopsRolloutFile  // file di gitopsPath yang diupdate CI (default: rollout.yaml)

    String buildTool      // kaniko | docker
    String buildBranch    // branch yang melakukan build (build-once)

    String slackChannel
    String agentLabel
    Boolean enableSecurityScan

    /**
     * Bangun config dari Map hasil readYaml.
     * @param raw Map dari .cicd/pipeline.yaml
     */
    static PipelineConfig fromMap(Map raw) {
        if (raw == null) {
            throw new IllegalArgumentException("pipeline.yaml kosong / tidak terbaca")
        }
        def cfg = new PipelineConfig()

        cfg.appName     = req(raw, 'app_name')
        cfg.language    = raw.get('language', 'generic')
        cfg.enableSecurityScan = raw.get('enable_security_scan', true) as Boolean

        def test        = (raw.get('test') ?: [:]) as Map
        cfg.testCommand = test.get('command', '')

        def registry        = req(raw, 'registry') as Map
        cfg.registryRegion  = req(registry, 'region')
        cfg.registryProject = req(registry, 'project_id')
        cfg.registryRepository = registry.get('repository', 'docker-images-repo')

        def gitops          = req(raw, 'gitops') as Map
        cfg.gitopsRepoUrl      = req(gitops, 'repo_url')
        cfg.gitopsBranch       = gitops.get('branch', 'main')
        cfg.gitopsPath         = req(gitops, 'path')
        cfg.gitopsRolloutFile  = gitops.get('rollout_file', 'rollout.yaml')

        def build           = (raw.get('build') ?: [:]) as Map
        cfg.buildTool       = build.get('tool', 'kaniko')
        cfg.buildBranch     = build.get('branch', 'development')

        def slack           = (raw.get('slack') ?: [:]) as Map
        cfg.slackChannel    = slack.get('channel', '')

        cfg.agentLabel      = raw.get('agent_label', 'vm-jenkins')

        cfg.validate()
        return cfg
    }

    private static Object req(Map m, String key) {
        def v = m?.get(key)
        if (v == null || (v instanceof String && v.trim().isEmpty())) {
            throw new IllegalArgumentException("pipeline.yaml: field wajib '${key}' kosong")
        }
        return v
    }

    void validate() {
        if (!(buildTool in ['kaniko', 'docker'])) {
            throw new IllegalArgumentException("build.tool harus 'kaniko' atau 'docker', bukan '${buildTool}'")
        }
    }

    /** Full image name di Artifact Registry (tanpa tag). */
    String imageName() {
        return "${registryRegion}-docker.pkg.dev/${registryProject}/${registryRepository}/${appName}"
    }
}
