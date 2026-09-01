import com.course.PipelineConfig

/**
 * updateGitops — perbarui image tag di repo GitOps, commit & push.
 *
 * Karena Kustomize images transformer tidak bekerja untuk CRD Rollout,
 * CI update image langsung di patch/rollout.yaml menggunakan sed.
 *
 * Pola dari produksi: lock('gitops') + rebase retry agar aman dari race condition.
 * ArgoCD mendeteksi perubahan commit → trigger sync → Argo Rollouts canary.
 *
 * Format image di patch/rollout.yaml:
 *   image: REGISTRY/APP:TAG  # <- CI replaces this line
 */
def call(PipelineConfig cfg, String gitSha) {
    def fullImage = "${cfg.imageName()}:${gitSha}"
    def rolloutFile = cfg.gitopsRolloutFile ?: 'rollout.yaml'
    def rolloutPath = "${cfg.gitopsPath}/${rolloutFile}"

    lock('gitops') {
        withCredentials([usernamePassword(
            credentialsId: 'github-jenkins-token',
            usernameVariable: 'GIT_USER',
            passwordVariable: 'GIT_TOKEN'
        )]) {
            dir('gitops-update') {
                deleteDir()
                retry(3) {
                    // Clone repo GitOps
                    sh """
                        git clone --depth=1 --branch ${cfg.gitopsBranch} \
                          https://\${GIT_USER}:\${GIT_TOKEN}@${cfg.gitopsRepoUrl} .
                    """

                    // Update image di patch/rollout.yaml menggunakan sed
                    // Matches: "image: <anything>" di dalam containers[] block
                    sh """
                        sed -i 's|image:.*docker.pkg.dev.*|image: ${fullImage}|g' ${rolloutPath}
                    """

                    // Verifikasi update
                    sh "grep 'image:' ${rolloutPath}"

                    // Commit + push dengan rebase untuk avoid race condition
                    sh """
                        git config user.email "jenkins@course.local"
                        git config user.name "jenkins-ci"
                        git add ${rolloutPath}
                        git diff --cached --quiet || git commit -m "ci: ${cfg.appName} image -> ${gitSha} [skip ci]"
                        git pull --rebase https://\${GIT_USER}:\${GIT_TOKEN}@${cfg.gitopsRepoUrl} ${cfg.gitopsBranch}
                        git push https://\${GIT_USER}:\${GIT_TOKEN}@${cfg.gitopsRepoUrl} HEAD:${cfg.gitopsBranch}
                    """
                }
            }
        }
    }
    echo "GitOps updated: ${rolloutPath} -> ${fullImage}"
    echo "ArgoCD akan detect commit ini dan trigger Rollout canary deployment."
}
