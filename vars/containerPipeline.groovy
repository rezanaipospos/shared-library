import com.course.PipelineConfig

/**
 * containerPipeline — entry point shared library untuk Pipeline job biasa (bukan Multibranch).
 *
 * Cara pakai dari Jenkins Pipeline script (inline di UI):
 *   @Library('course-shared-library') _
 *   containerPipeline(
 *       appRepoUrl: 'https://github.com/ORG/backend-go.git',
 *       configPath: '.cicd/pipeline.yaml'  // path relatif di dalam repo app
 *   )
 *
 * Flow:
 *   1. Checkout repo app (appRepoUrl) ke branch dari webhook param pr_base_branch
 *   2. Baca .cicd/pipeline.yaml dari workspace
 *   3. Test → Build & Push → Update GitOps → Notify Slack
 *
 * Params:
 *   appRepoUrl  : URL repo aplikasi (wajib — karena repo terpisah dari shared library)
 *   configPath  : path ke pipeline.yaml di dalam repo app (default: .cicd/pipeline.yaml)
 */
def call(Map args = [:]) {
    def appRepoUrl  = args.get('appRepoUrl') ?: env.APP_REPO_URL ?: env.GIT_URL
    def configPath  = args.get('configPath', '.cicd/pipeline.yaml')
    def cfg         = null
    def gitSha      = null
    def branchName  = null

    if (!appRepoUrl) {
        error("containerPipeline: 'appRepoUrl' tidak ditemukan. Pass via parameter atau pastikan job config punya SCM URL (env.GIT_URL).")
    }

    node {
        try {
            stage('Checkout App Repo') {
                // Branch dari webhook Generic Webhook Trigger param pr_base_branch
                // Fallback ke 'development' jika tidak ada
                branchName = env.pr_base_branch ?: 'development'

                git branch: branchName,
                    credentialsId: 'github-jenkins-token',
                    url: appRepoUrl

                gitSha = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                echo "Checked out: ${appRepoUrl} @ ${branchName} (${gitSha})"

                // Baca pipeline.yaml dari workspace (hasil checkout repo app)
                def raw = readYaml(file: configPath)
                cfg = PipelineConfig.fromMap(raw)

                echo "App: ${cfg.appName} | branch: ${branchName} | sha: ${gitSha} | image: ${cfg.imageName()}"
                //notifySlack(cfg.slackChannel, 'STARTED', "Build ${cfg.appName} @ ${branchName} (${gitSha})", null)
            }

            if (cfg.enableSecurityScan) {
                stage('Security Scan (Source)') {
                    trivyScan(type: 'fs', target: '.', failOnVuln: true)
                }
            }

            // stage('Test') {
            //     if (cfg.testCommand?.trim()) {
            //         sh cfg.testCommand
            //     } else {
            //         echo 'Tidak ada test.command di pipeline.yaml — dilewati.'
            //     }
            // }

            // Build & push hanya untuk branch yang sesuai build.branch
            if (branchName == cfg.buildBranch) {
                stage('Build & Push') {
                    buildAndPush(cfg, gitSha)
                }
                stage('Update GitOps') {
                    updateGitops(cfg, gitSha)
                }
            } else {
                echo "Branch '${branchName}' != build branch '${cfg.buildBranch}'. Skip build."
            }

            //notifySlack(cfg.slackChannel, 'SUCCESS', "Build ${cfg.appName} @ ${branchName} (${gitSha}) berhasil.", null)

        } catch (Exception e) {
            // if (cfg != null) {
            //     notifySlack(cfg.slackChannel, 'FAILURE', "Build gagal: ${e.getMessage()}", null)
            // }
            throw e
        }
    }
}
