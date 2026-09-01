/**
 * trivyScan — Menjalankan pemindaian keamanan menggunakan Trivy (Docker-based).
 *
 * Params:
 *   type       : tipe scanning ('fs' untuk source/dependencies/secrets, 'image' untuk Docker image)
 *   target     : direktori target (untuk 'fs', default: '.') atau tag image (untuk 'image')
 *   failOnVuln : jika true, pipeline akan gagal jika ditemukan celah HIGH atau CRITICAL (exit-code 1)
 */
def call(Map args = [:]) {
    def type       = args.get('type', 'fs')
    def target     = args.get('target', '.')
    def failOnVuln = args.get('failOnVuln', true)
    def exitCode   = failOnVuln ? "1" : "0"

    // Gunakan direktori cache persisten di VM Jenkins agar scan lebih cepat pada run berikutnya
    def cacheVolume = "-v \$HOME/.cache/trivy:/root/.cache/"

    if (type == 'fs') {
        echo "=== [DevSecOps] Memulai Trivy FileSystem, Secrets, & IaC Scan ==="
        // Scan filesystem untuk vulnerability (SCA), secret leak, dan miskonfigurasi IaC
        sh """
            docker run --rm \
              -v \$(pwd):/workspace \
              ${cacheVolume} \
              aquasec/trivy:latest fs \
              --scanners vuln,secret,config \
              --severity HIGH,CRITICAL \
              --exit-code ${exitCode} \
              /workspace
        """
    } else if (type == 'image') {
        echo "=== [DevSecOps] Memulai Trivy Container Image Scan pada: ${target} ==="
        // Scan docker image local menggunakan docker socket
        sh """
            docker run --rm \
              -v /var/run/docker.sock:/var/run/docker.sock \
              ${cacheVolume} \
              aquasec/trivy:latest image \
              --severity HIGH,CRITICAL \
              --exit-code ${exitCode} \
              ${target}
        """
    }
}
