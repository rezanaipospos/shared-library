import com.course.PipelineConfig

/**
 * buildAndPush — build & push image ke Google Artifact Registry.
 *
 * Mode:
 * - docker (default untuk VM agent): docker build + docker push
 * - kaniko: untuk K8s agent (rootless, tanpa Docker daemon, AD-13)
 *
 * Auth ke GCP Artifact Registry:
 * - VM dengan GCP service account: `gcloud auth configure-docker REGION-docker.pkg.dev`
 * - Atau via Docker credential helper: docker-credential-gcr
 *
 * Tag = git short SHA (immutable, traceable, AD-14)
 */
def call(PipelineConfig cfg, String gitSha) {
    def image = "${cfg.imageName()}:${gitSha}"
    def latestImage = "${cfg.imageName()}:latest"

    if (cfg.buildTool == 'kaniko') {
        echo "Building dengan Kaniko: ${image}"
        sh """
            docker run --rm \
              -v \$(pwd):/workspace \
              -v \$HOME/.docker:/kaniko/.docker:ro \
              gcr.io/kaniko-project/executor:latest \
              --context=dir:///workspace \
              --dockerfile=/workspace/Dockerfile \
              --destination=${image} \
              --destination=${latestImage} \
              --cache=true
        """
    } else {
        // Docker mode — pastikan VM Jenkins punya docker + akses GCP Artifact Registry
        echo "Building dengan Docker: ${image}"

        // Konfigurasi auth ke GCP Artifact Registry (region-docker.pkg.dev)
        // Requires: gcloud CLI + service account dengan roles/artifactregistry.writer
        sh "gcloud auth configure-docker ${cfg.registryRegion}-docker.pkg.dev --quiet"

        sh "docker build -t ${image} -t ${latestImage} ."

        if (cfg.enableSecurityScan) {
            trivyScan(type: 'image', target: image, failOnVuln: true)
        }

        sh "docker push ${image}"
        sh "docker push ${latestImage}"
    }

    echo "Image pushed: ${image}"
    return image
}
